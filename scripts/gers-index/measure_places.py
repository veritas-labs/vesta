#!/usr/bin/env python3
"""ADR-039 WP-P0: measure the places directory before building it. Measurement only: no uploads, no network except an
anonymous read of Overture's S3 for step `hierarchy`. Run with the scratch venv (`.venv/bin/python -I`). Steps, each cached
under <scratch>/places/ and restartable:
  contain   place -> smallest containing locality polygon (exact ST_Intersects join, area in degrees^2 as gers_index.py does)
  near      for places in no polygon, nearest polygon locality and its distance in metres (EPSG:27700, bbox-prefiltered to 25 km)
  report    (a) coverage by cap, (b) places per locality, (c) per-chunk vs per-unit zlib, (d) chunks at 256 KiB, (f) largest locality
  hierarchy (e) taxonomy.hierarchy coverage and top-level groups from Overture 2026-09-23.1 (GB, id + hierarchy only)
Memory is capped at 12 GB with spill to <work>/tmp, like gers_index.py. Results are quoted in ADR-039 "Places measurements".
"""
import os, sys, time, zlib, struct, random, statistics
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import duckdb
import gers_index as g

W = os.path.expanduser("~/Development/scratch/gers-index"); P = f"{W}/places"; os.makedirs(P, exist_ok=True)
CAP_M = 25000; CHUNK = 256 * 1024

def con():
    os.makedirs(f"{W}/tmp", exist_ok=True)
    c = duckdb.connect(f"{P}/m.duckdb")
    for e in ("spatial", "h3"):
        c.sql(f"INSTALL {e}" + (" FROM community" if e == "h3" else "")); c.sql(f"LOAD {e}")
    c.sql(f"SET memory_limit='12GB'; SET temp_directory='{W}/tmp'; SET max_temp_directory_size='100GB'; SET threads=6")
    return c

def contain(c):
    t = time.time()
    c.sql(f"""CREATE OR REPLACE TABLE a AS SELECT division_id, ST_GeomFromText(wkt) g FROM '{W}/GB_area.parquet'""")
    c.sql("ALTER TABLE a ADD COLUMN ar DOUBLE; UPDATE a SET ar = ST_Area(g)")
    c.sql(f"CREATE OR REPLACE TABLE p AS SELECT row_number() OVER () AS rn, id, ST_Point(lon, lat) pt, lon, lat FROM '{W}/gb_places.parquet'")
    c.sql("""CREATE OR REPLACE TABLE inside AS SELECT p.rn, arg_min(a.division_id, a.ar) div
             FROM p JOIN a ON ST_Intersects(a.g, p.pt) GROUP BY p.rn""")
    print("inside", c.sql("select count(*) from inside").fetchone()[0], f"{time.time()-t:.0f}s", flush=True)

def near(c):
    t = time.time()
    c.sql("CREATE OR REPLACE TABLE ax AS SELECT division_id, ST_Transform(g,'EPSG:4326','EPSG:27700',always_xy:=true) g, ST_Area(g) ar FROM a")
    c.sql("""CREATE OR REPLACE TABLE ab AS SELECT division_id, g, ST_XMin(g) x0, ST_XMax(g) x1, ST_YMin(g) y0, ST_YMax(g) y1 FROM ax""")
    c.sql("""CREATE OR REPLACE TABLE rest AS SELECT rn, ST_Transform(pt,'EPSG:4326','EPSG:27700',always_xy:=true) m FROM p
             WHERE rn NOT IN (SELECT rn FROM inside)""")
    c.sql("ALTER TABLE rest ADD COLUMN x DOUBLE; ALTER TABLE rest ADD COLUMN y DOUBLE; UPDATE rest SET x=ST_X(m), y=ST_Y(m)")
    n = c.sql("select count(*) from rest").fetchone()[0]; print("rest", n, flush=True)
    c.sql("CREATE OR REPLACE TABLE near AS SELECT * FROM (SELECT NULL::BIGINT rn, NULL::VARCHAR div, NULL::DOUBLE d) WHERE false")
    step = 200000
    for lo in range(1, n + 1, step):
        c.sql(f"""INSERT INTO near SELECT rn, arg_min(division_id, d), min(d) FROM (
            SELECT r.rn, b.division_id, ST_Distance(b.g, r.m) d FROM (SELECT * FROM rest ORDER BY rn LIMIT {step} OFFSET {lo-1}) r
            JOIN ab b ON r.x BETWEEN b.x0-{CAP_M} AND b.x1+{CAP_M} AND r.y BETWEEN b.y0-{CAP_M} AND b.y1+{CAP_M}) WHERE d <= {CAP_M} GROUP BY rn""")
        print("  near batch", lo, f"{time.time()-t:.0f}s", flush=True)
    print("near", c.sql("select count(*) from near").fetchone()[0], f"{time.time()-t:.0f}s")

STEPS = {}
def main():
    c = con()
    for s in sys.argv[1:]: STEPS[s](c)

def pct(v, q): v = sorted(v); return v[min(len(v)-1, int(q*len(v)))]

def load(c):
    """Per place: (division_id, distance m or 0, ...) joined to names-table row index."""
    ids, st, par, names, cent = g.decode_names(open(f"{W}/out/names.bin", "rb").read())
    row = {g.fmt_uuid(h): i for i, h in enumerate(ids)}
    c.sql(f"""CREATE OR REPLACE TABLE asg AS
      SELECT p.rn, pl.id, pl.pname, pl.cat, pl.confidence, p.lat, p.lon,
             h3_latlng_to_cell(p.lat, p.lon, 9) h9, coalesce(i.div, n.div) div, CASE WHEN i.rn IS NULL THEN n.d ELSE 0 END d
      FROM p JOIN '{W}/gb_places.parquet' pl ON pl.id = p.id
      LEFT JOIN inside i ON i.rn = p.rn LEFT JOIN near n ON n.rn = p.rn""")
    return row

def units(c, row, cap):
    """Per locality (names-table row), the encoded unit blobs split into sub-units of <= SUB raw bytes. Layout per plan 'Chunk formats'."""
    cats = [r[0] for r in c.sql("select cat from asg group by cat order by count(*) desc").fetchall()]
    ci = {k: i for i, k in enumerate(cats)}
    q = c.sql(f"select div, id, pname, cat, confidence, lat, lon from asg where div is not null and d <= {cap} order by div, h9, id")
    out = {}; cur = None; buf = []
    def flush(div):
        if div is not None and buf: out[div] = list(buf)
    while True:
        rows = q.fetchmany(200000)
        if not rows: break
        for r in rows:
            if r[0] != cur: flush(cur); cur = r[0]; buf = []
            buf.append(r)
    flush(cur)
    return out, ci

def enc_unit(pl, ci):
    b = bytearray(g.varint(len(pl)))
    b += b"".join(bytes.fromhex(r[1].replace("-", "")) for r in pl)   # ids: 16 B each, incompressible
    for k in (5, 6):
        prev = 0
        for r in pl: v = round(r[k] * 1e6); b += g.varint(g.zigzag(v - prev)); prev = v
    for r in pl: b += g.varint(ci[r[3]])
    b += bytes(min(255, round((r[4] or 0) * 255)) for r in pl)
    for r in pl: b += (r[2] or "").encode() + b"\0"
    return bytes(b)

def report(c):
    row = load(c)
    tot = c.sql("select count(*) from asg").fetchone()[0]
    ins = c.sql("select count(*) from asg where d = 0 and div is not null").fetchone()[0]
    print(f"(a) total {tot}  inside {ins} ({100*ins/tot:.1f}%)")
    for km in (1, 2, 5, 10, 25):
        n = c.sql(f"select count(*) from asg where d > 0 and d <= {km*1000}").fetchone()[0]
        print(f"    within {km} km (outside): {n} ({100*n/tot:.1f}%)  cumulative placed {ins+n} ({100*(ins+n)/tot:.1f}%)")
    print("    id sample", c.sql("select id from asg limit 1").fetchone())
    res = {}
    for cap in (0, 2000, 5000, 10000, 25000):
        us, ci = units(c, row, cap)
        cnt = sorted(len(v) for v in us.values()); nloc = len(row)
        full = cnt + [0] * (nloc - len(cnt))
        loc_with = len(cnt)
        print(f"(b) cap {cap/1000:g} km: placed {sum(cnt)}, localities with places {loc_with}/{nloc}; per locality (with places) median {statistics.median(cnt):g} p90 {pct(cnt,.9)} p99 {pct(cnt,.99)} max {cnt[-1]}")
        res[cap] = (us, ci)
    return res

def subunits(pl, ci, raw_limit):
    """Split a locality's places (already in H3 res-9 cell order) into sub-units whose encoded size is <= raw_limit."""
    out = []; i = 0
    while i < len(pl):
        n = max(1, min(len(pl) - i, raw_limit // 40))          # first guess from ~40 B/place, then trim to fit
        while True:
            b = enc_unit(pl[i:i+n], ci)
            if len(b) <= raw_limit or n == 1: break
            n = max(1, int(n * raw_limit / len(b) * 0.97))
        out.append((i, n, b)); i += n
    return out

def pack(us, ci, raw_limit, per_unit):
    """Greedy packing in names-table row order. Per-chunk mode: a chunk is zlib(concat of units) and must compress to <= CHUNK.
    Per-unit mode: each unit is zlib'd alone, the chunk is the concatenation and must be <= CHUNK."""
    chunks = []; cur = []; cur_raw = 0; cur_cmp = 0; where = {}          # where[row] = [(chunk, off, len, n)]
    sub = {r: subunits(pl, ci, raw_limit) for r, pl in us.items()}
    def close():
        nonlocal cur, cur_raw, cur_cmp
        if cur:
            if per_unit: chunks.append(cur_cmp)
            else: chunks.append(len(zlib.compress(b"".join(x[1] for x in cur), 6)))
        cur = []; cur_raw = 0; cur_cmp = 0
    for r in sorted(sub):
        for (i, n, b) in sub[r]:
            if per_unit:
                z = zlib.compress(b, 6); sz = len(z)
                if cur_cmp + sz > CHUNK: close()
                where.setdefault(r, []).append((len(chunks), cur_cmp, sz, n)); cur.append((r, z)); cur_cmp += sz
            else:
                if cur_raw + len(b) > raw_limit * 1: close()
                where.setdefault(r, []).append((len(chunks), cur_raw, len(b), n)); cur.append((r, b)); cur_raw += len(b)
    close()
    return chunks, where

def sizing(c):
    row = load(c); cap = 5000
    us_by_id, ci = units(c, row, cap)
    us = {row[d]: pl for d, pl in us_by_id.items() if d in row}
    print("localities with places", len(us), "places", sum(len(v) for v in us.values()))
    allraw = sum(len(enc_unit(pl, ci)) for pl in us.values())
    print(f"raw column bytes (one unit per locality) {allraw}  = {allraw/sum(len(v) for v in us.values()):.1f} B/place")
    # (c) sample: every 20th locality, compressed three ways
    keys = sorted(us)[::20]
    perunit = sum(len(zlib.compress(enc_unit(us[k], ci), 6)) for k in keys)
    cat = b"".join(enc_unit(us[k], ci) for k in keys)
    perchunk = len(zlib.compress(cat, 6)); raw = len(cat)
    print(f"(c) sample of {len(keys)} localities: raw {raw}, per-unit zlib {perunit} ({perunit/raw:.3f}), one stream zlib {perchunk} ({perchunk/raw:.3f}), per-unit costs {100*(perunit/perchunk-1):.1f}% more")
    # (d) pack, tuning raw_limit so every per-chunk chunk compresses to <= CHUNK
    ratio = perchunk / raw; limit = int(CHUNK / ratio * 0.97)
    while True:
        chunks, where = pack(us, ci, limit, per_unit=False)
        if max(chunks) <= CHUNK: break
        limit = int(limit * 0.97)
    tot = sum(chunks)
    print(f"(d) per-chunk zlib: raw limit {limit}, {len(chunks)} chunks, total {tot} B ({tot/2**20:.1f} MiB), mean {tot//len(chunks)} B, max {max(chunks)}")
    pc, pw = pack(us, ci, CHUNK, per_unit=True)
    print(f"    per-unit zlib: {len(pc)} chunks, total {sum(pc)} B ({sum(pc)/2**20:.1f} MiB)")
    # directory size (VGP2): per row varint range count, then (chunk, offset, length, count) tuples
    d = bytearray()
    for r in range(len(row)):
        w = where.get(r, []); d += g.varint(len(w))
        for t in w:
            for v in t: d += g.varint(v)
    print(f"    directory raw {len(d)} B, zlib {len(zlib.compress(bytes(d), 9))} B; root with {len(chunks)} chunk addresses = {len(chunks)*32} B")
    # (b) per-locality chunks touched, (f) largest locality
    span = {r: len({t[0] for t in w}) for r, w in where.items()}
    sp = sorted(span.values())
    print(f"    chunks touched per locality: median {statistics.median(sp):g} p90 {pct(sp,.9)} p99 {pct(sp,.99)} max {sp[-1]}; {sum(1 for v in sp if v==1)} of {len(sp)} fit in one chunk")
    ids, st, par, names, cent = g.decode_names(open(f"{W}/out/names.bin", "rb").read())
    big = sorted(us, key=lambda r: -len(us[r]))[:5]
    for r in big:
        w = where[r]; nb = len({t[0] for t in w})
        print(f"(f) {names[r]!r} ({st[r]}): {len(us[r])} places, {sum(t[2] for t in w)} raw B, spans {nb} chunks (~{nb*tot/len(chunks)/1024:.0f} KiB fetched), {len(w)} sub-units")
    # a home + 10 nearest neighbours: distinct chunks
    rnd = random.Random(7); rows = [r for r in us if cent[r] != (0, 0)]; cr = []
    for r in rnd.sample(rows, 300):
        nn = sorted(rows, key=lambda q: (cent[q][0]-cent[r][0])**2 + ((cent[q][1]-cent[r][1])*0.6)**2)[:11]
        cr.append(len({t[0] for q in nn for t in where.get(q, [])}))
    cr.sort(); print(f"    home + 10 nearest localities (300 random homes): distinct chunks median {statistics.median(cr):g} p90 {pct(cr,.9)} max {cr[-1]}")
    # bytes fetched for median locality
    print(f"    mean chunk {tot//len(chunks)} B: a locality of the median size still fetches a whole chunk")

PLACES_S3 = "s3://overturemaps-us-west-2/release/" + g.RELEASE + "/theme=places/type=place/*.parquet"

def hierarchy(c):
    """(e) taxonomy.hierarchy coverage for our GB ids: reads only id + taxonomy.hierarchy (+ bbox for row-group pruning)."""
    t = time.time()
    c.sql("INSTALL httpfs; LOAD httpfs; SET s3_region='us-west-2'")
    c.sql(f"""COPY (SELECT id, taxonomy.hierarchy AS h FROM read_parquet('{PLACES_S3}')
        WHERE bbox.xmin BETWEEN -9 AND 2.1 AND bbox.ymin BETWEEN 49.8 AND 61) TO '{P}/hier_gb_bbox.parquet' (FORMAT parquet, COMPRESSION zstd)""")
    print("bbox rows", c.sql(f"select count(*) from '{P}/hier_gb_bbox.parquet'").fetchone()[0], f"{time.time()-t:.0f}s")
    c.sql(f"""CREATE OR REPLACE TABLE hier AS SELECT g.id, h.h FROM '{W}/gb_places.parquet' g LEFT JOIN '{P}/hier_gb_bbox.parquet' h USING (id)""")
    print("our ids", c.sql("select count(*) from hier").fetchone()[0], "found in release", c.sql("select count(*) from hier where h is not null").fetchone()[0],
          "with non-empty hierarchy", c.sql("select count(*) from hier where len(h) > 0").fetchone()[0])
    print("depth", c.sql("select len(h) d, count(*) from hier where h is not null group by 1 order by 1").fetchall())
    r = c.sql("select h[1] grp, count(*) n from hier where len(h) > 0 group by 1 order by n desc").fetchall()
    print("top-level groups", len(r)); [print(f"   {a}: {b}") for a, b in r]
    print("group by hierarchy[1] vs primary category mismatches (places whose cat is not in own hierarchy):",
          c.sql(f"select count(*) from hier join '{W}/gb_places.parquet' g using (id) where len(h)>0 and not list_contains(h, g.cat)").fetchone()[0])

STEPS.update(contain=contain, near=near, report=report, sizing=sizing, hierarchy=hierarchy)
if __name__ == "__main__": main()
