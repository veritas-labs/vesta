#!/usr/bin/env python3
"""Build the locality-only GERS index for one country and publish it to an Autonomi devnet (ADR-039).

Subcommands (run in order; each is restartable):
  extract  Overture S3 -> <work>/<cc>_area.parquet (locality polygons) and <cc>_div.parquet (all divisions)
  cells    smallest locality per res-9 H3 cell -> table `cells` in <work>/work.duckdb
  adjacency  touching localities (nested ones excluded) -> <work>/<cc>_edges.parquet
  build    encode shards, names table, adjacency; upload chunks; set the pointer -> <work>/out/published.json
  verify   fetch everything back over the network and compare with point-in-polygon on sample points

See README.md in this directory. Format notes are in the docstrings of the encode_* functions.
"""
import argparse, bisect, json, os, random, struct, subprocess, sys, time, zlib
import duckdb

RELEASE = "2026-09-23.1"
SHARD_RES = 2
S3 = "s3://overturemaps-us-west-2/release/{rel}/theme=divisions/type={t}/*.parquet"
DEVNET = os.environ.get("GERS_DEVNET_DIR", os.path.expanduser("~/Development/scratch/ant-devnet-data"))
ENV = dict(os.environ, XDG_DATA_HOME=DEVNET,   # Anvil account #0: a public test key with no real value
           SECRET_KEY="0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80")
ANT = ["ant", "--devnet-manifest", f"{DEVNET}/ant/devnet-manifest.json", "--allow-loopback", "--evm-network", "local"]
OVERLAP_NESTED = 0.2   # a pair is "nested", not neighbours, if more than this share of the smaller polygon is inside the larger

def connect(work, ext=("spatial", "h3")):
    os.makedirs(f"{work}/tmp", exist_ok=True)
    c = duckdb.connect(f"{work}/work.duckdb")
    for e in ext:
        c.sql(f"INSTALL {e}" + (" FROM community" if e == "h3" else "")); c.sql(f"LOAD {e}")
    c.sql(f"SET memory_limit='12GB'; SET temp_directory='{work}/tmp'; SET max_temp_directory_size='100GB'; SET threads=6")
    return c

# ---- H3 / varint helpers -------------------------------------------------------------------------------------------
def ordinal(cell):          # base-7 digits of the nine resolution digits, after the base cell
    o = (cell >> 45) & 0x7f
    for i in range(1, 10): o = o*7 + ((cell >> (3*(15-i))) & 7)
    return o
def prefix_key(cell, res=SHARD_RES):
    k = (cell >> 45) & 0x7f
    for i in range(1, res+1): k = k*7 + ((cell >> (3*(15-i))) & 7)
    return k
def varint(n):
    out = bytearray()
    while True:
        b = n & 0x7f; n >>= 7
        if n: out.append(b | 0x80)
        else: out.append(b); return bytes(out)
def read_varint(buf, i):
    n = s = 0
    while True:
        b = buf[i]; i += 1; n |= (b & 0x7f) << s; s += 7
        if not b & 0x80: return n, i
def zigzag(d): return d*2 if d >= 0 else -d*2-1
def unzigzag(z): return z >> 1 if not z & 1 else -((z+1) >> 1)
def fmt_uuid(h): return f"{h[:8]}-{h[8:12]}-{h[12:16]}-{h[16:20]}-{h[20:]}"

# ---- ant CLI -------------------------------------------------------------------------------------------------------
def ant(*a):
    r = subprocess.run(ANT + list(a), env=ENV, capture_output=True, text=True)
    if r.returncode: raise RuntimeError(r.stderr + r.stdout)
    lines = r.stdout.strip().splitlines()
    return lines[-1] if lines else ""
def put_chunk(path): return ant("chunk", "put", path)
def get_chunk(addr, path):
    t = time.time(); ant("chunk", "get", addr, "-o", path); dt = time.time()-t
    return open(path, "rb").read(), dt

# ---- formats -------------------------------------------------------------------------------------------------------
def encode_shard(lst):
    """lst: sorted [(ordinal, division_uuid)]. zlib( varint nDivs, nDivs*16 B uuid, varint nRuns, nRuns*(gap, len, divIndex) )
    A run is a stretch of consecutive res-9 ordinals with the same division; gap = start - previous run end."""
    runs = []
    for o, dv in lst:
        if runs and runs[-1][2] == dv: runs[-1][1] = o   # merges across absent cells: smaller, but a cell with no locality inside a run answers wrongly
        else: runs.append([o, o, dv])
    local = {}
    for r in runs: local.setdefault(r[2], len(local))
    body = bytearray(varint(len(local)))
    for u in local: body += bytes.fromhex(u.replace("-", ""))
    body += varint(len(runs)); prev = 0
    for s, e, dv in runs:
        body += varint(s-prev) + varint(e-s) + varint(local[dv]); prev = e
    return zlib.compress(bytes(body), 9)

def decode_shard(blob):
    b = zlib.decompress(blob); n, i = read_varint(b, 0)
    uuids = [b[i+16*k: i+16*k+16].hex() for k in range(n)]; i += 16*n
    nr, i = read_varint(b, i); starts, ends, divs = [], [], []; prev = 0
    for _ in range(nr):
        g, i = read_varint(b, i); l, i = read_varint(b, i); d, i = read_varint(b, i)
        s = prev+g; starts.append(s); ends.append(s+l); divs.append(d); prev = s+l
    return uuids, starts, ends, divs

def encode_names(rows, subtypes):
    """rows: [(uuid, subtype, parent_row_index_or_-1, name)] in table order. zlib( varint nSub, nSub*(NUL-terminated name),
    varint n, n*16 B uuid, n subtype bytes, n*varint (parent index + 1, 0 = none), n*(NUL-terminated name) )."""
    sub = {s: i for i, s in enumerate(subtypes)}
    b = bytearray(varint(len(subtypes)))
    for s in subtypes: b += s.encode() + b"\0"
    b += varint(len(rows))
    b += b"".join(bytes.fromhex(r[0].replace("-", "")) for r in rows)
    b += bytes(sub[r[1]] for r in rows)
    b += b"".join(varint(r[2]+1) for r in rows)
    b += b"".join((r[3] or "").encode() + b"\0" for r in rows)
    return zlib.compress(bytes(b), 9)

def decode_names(blob):
    b = zlib.decompress(blob); ns, i = read_varint(b, 0); subtypes = []
    for _ in range(ns):
        j = b.index(0, i); subtypes.append(b[i:j].decode()); i = j+1
    n, i = read_varint(b, i)
    ids = [b[i+16*k: i+16*k+16].hex() for k in range(n)]; i += 16*n
    st = [subtypes[x] for x in b[i:i+n]]; i += n
    par = []
    for _ in range(n): p, i = read_varint(b, i); par.append(p-1)
    names = []
    for _ in range(n):
        j = b.index(0, i); names.append(b[i:j].decode()); i = j+1
    return ids, st, par, names

def encode_adjacency(adj, n):
    """zlib( n * (varint degree, degree * varint zigzag(index - previous index)) ), row i's first delta is against i."""
    body = bytearray()
    for i in range(n):
        ns = sorted(adj.get(i, ())); body += varint(len(ns)); p = i
        for x in ns: body += varint(zigzag(x-p)); p = x
    return zlib.compress(bytes(body), 9)

def decode_adjacency(blob, n):
    b = zlib.decompress(blob); i = 0; out = []
    for r in range(n):
        d, i = read_varint(b, i); p = r; ns = []
        for _ in range(d): z, i = read_varint(b, i); p = p + unzigzag(z); ns.append(p)
        out.append(ns)
    return out

ROOT_HDR = struct.Struct("<4sB16s2s32s32sH")   # magic VGI2, shard res, release, country, names addr, adjacency addr, nShards
def encode_root(release, country, names_addr, adj_addr, entries):
    r = ROOT_HDR.pack(b"VGI2", SHARD_RES, release.encode().ljust(16), country.encode(), bytes.fromhex(names_addr), bytes.fromhex(adj_addr), len(entries))
    for k, a in entries: r += struct.pack("<I", k) + bytes.fromhex(a)
    return r
def parse_root(b):
    magic, res, rel, cc, na, aa, n = ROOT_HDR.unpack_from(b); assert magic == b"VGI2"
    m = {}
    for j in range(n):
        o = ROOT_HDR.size + 36*j; k, = struct.unpack("<I", b[o:o+4]); m[k] = b[o+4:o+36].hex()
    return dict(res=res, release=rel.decode().strip(), country=cc.decode(), names=na.hex(), adjacency=aa.hex(), shards=m)

# ---- subcommands ---------------------------------------------------------------------------------------------------
def cmd_extract(a):
    c = connect(a.work, ("spatial",)); c.sql("INSTALL httpfs; LOAD httpfs; SET s3_region='us-west-2'")
    cc = a.country; t = time.time()
    c.sql(f"""COPY (SELECT division_id, subtype, names.primary AS name, ST_AsText(geometry) AS wkt
      FROM read_parquet('{S3.format(rel=a.release, t="division_area")}') WHERE country='{cc}' AND class='land' AND subtype='locality')
      TO '{a.work}/{cc}_area.parquet' (FORMAT parquet, COMPRESSION zstd)""")
    print("areas", c.sql(f"select count(*) from '{a.work}/{cc}_area.parquet'").fetchone()[0], f"{time.time()-t:.0f}s")
    c.sql(f"""COPY (SELECT id, subtype, parent_division_id, names.primary AS name
      FROM read_parquet('{S3.format(rel=a.release, t="division")}') WHERE country='{cc}')
      TO '{a.work}/{cc}_div.parquet' (FORMAT parquet, COMPRESSION zstd)""")
    print("divisions", c.sql(f"select count(*) from '{a.work}/{cc}_div.parquet'").fetchone()[0], f"{time.time()-t:.0f}s")

def cmd_cells(a):
    """Smallest locality containing each res-9 cell. MULTIPOLYGONs are split first: h3_polygon_wkt_to_cells returns nothing for them."""
    c = connect(a.work); cc = a.country; t = time.time()
    c.sql(f"""CREATE OR REPLACE TABLE cells AS
      WITH a0 AS (SELECT division_id, ST_GeomFromText(wkt) g FROM '{a.work}/{cc}_area.parquet'),
      ar AS (SELECT division_id, ST_Area(g) ar FROM a0),
      parts AS (SELECT division_id, ST_AsText(u.geom) wkt FROM a0, UNNEST(ST_Dump(g)) t(u)),
      f AS (SELECT division_id, unnest(h3_polygon_wkt_to_cells(wkt, 9)) cell FROM parts),
      fr AS (SELECT DISTINCT f.cell, f.division_id, ar.ar FROM f JOIN ar USING (division_id))
      SELECT cell, arg_min(division_id, ar) div FROM fr GROUP BY cell""")
    print("cells", c.sql("select count(*) from cells").fetchone()[0], "divisions", c.sql("select count(distinct div) from cells").fetchone()[0], f"{time.time()-t:.0f}s")
    empty = c.sql(f"select count(*) from '{a.work}/{cc}_area.parquet' where division_id not in (select div from cells)").fetchone()[0]
    print(f"localities with polygons but no cell of their own (smaller than a res-9 cell, or covered by nested ones): {empty}")

def cmd_adjacency(a):
    c = connect(a.work); cc = a.country; t = time.time()
    c.sql(f"CREATE OR REPLACE TABLE a AS SELECT division_id id, ST_GeomFromText(wkt) g FROM '{a.work}/{cc}_area.parquet'")
    c.sql(f"""CREATE OR REPLACE TABLE e AS SELECT x.id a, y.id b, ST_Area(ST_Intersection(x.g, y.g)) / least(ST_Area(x.g), ST_Area(y.g)) ov
      FROM a x JOIN a y ON x.id < y.id AND ST_Intersects(x.g, y.g)""")
    c.sql(f"COPY (SELECT a, b FROM e WHERE ov <= {OVERLAP_NESTED}) TO '{a.work}/{cc}_edges.parquet' (FORMAT parquet)")
    print("touching pairs", c.sql(f"select count(*) from e where ov <= {OVERLAP_NESTED}").fetchone()[0], "nested pairs dropped", c.sql(f"select count(*) from e where ov > {OVERLAP_NESTED}").fetchone()[0], f"{time.time()-t:.0f}s")

def table_rows(c, work, cc):
    """Names-table rows: every locality with a polygon, spatially ordered (H3 res 6 of the centroid), then their ancestors."""
    rows = {r[0]: r for r in c.sql(f"select id, subtype, parent_division_id, name from '{work}/{cc}_div.parquet'").fetchall()}
    loc = [r[0] for r in c.sql(f"""select division_id from '{work}/{cc}_area.parquet'
        order by h3_latlng_to_cell(ST_Y(ST_Centroid(ST_GeomFromText(wkt))), ST_X(ST_Centroid(ST_GeomFromText(wkt))), 6), division_id""").fetchall()]
    keep, seen = [], set(loc)
    for u in loc:
        x = rows[u][2]
        while x and x in rows and x not in seen: seen.add(x); keep.append(x); x = rows[x][2]
    order = loc + sorted(keep); ix = {u: i for i, u in enumerate(order)}
    return [(u, rows[u][1], ix.get(rows[u][2], -1), rows[u][3]) for u in order], ix

def cmd_build(a):
    c = connect(a.work); cc = a.country; out = f"{a.work}/out"; os.makedirs(out, exist_ok=True)
    for f in os.listdir(out): os.remove(f"{out}/{f}") if f.endswith(".bin") else None
    up = (lambda p: put_chunk(p)) if not a.no_upload else (lambda p: "0"*64)
    shards = {}
    for cell, dv in c.sql("select cell, div from cells order by cell").fetchall():
        shards.setdefault(prefix_key(cell), []).append((ordinal(cell), dv))
    entries = []; total = 0
    for k in sorted(shards):
        blob = encode_shard(shards[k]); p = f"{out}/shard_{k}.bin"; open(p, "wb").write(blob)
        entries.append((k, up(p))); total += len(blob)
    print(f"{len(entries)} shards, {total} B, largest {max(os.path.getsize(f'{out}/shard_{k}.bin') for k in shards)} B", flush=True)
    rows, ix = table_rows(c, a.work, cc)
    subtypes = sorted({r[1] for r in rows}); nb = encode_names(rows, subtypes); open(f"{out}/names.bin", "wb").write(nb)
    adj = {}
    for x, y in c.sql(f"select a, b from '{a.work}/{cc}_edges.parquet'").fetchall():
        adj.setdefault(ix[x], set()).add(ix[y]); adj.setdefault(ix[y], set()).add(ix[x])
    ab = encode_adjacency(adj, len(rows)); open(f"{out}/adjacency.bin", "wb").write(ab)
    print(f"names table {len(rows)} rows {len(nb)} B; adjacency {len(ab)} B ({sum(map(len, adj.values()))//2} edges)")
    na, aa = up(f"{out}/names.bin"), up(f"{out}/adjacency.bin")
    root = encode_root(a.release, cc, na, aa, entries); open(f"{out}/root.bin", "wb").write(root); ra = up(f"{out}/root.bin")
    pub = dict(country=cc, release=a.release, root=ra, names=na, adjacency=aa, shards=len(entries), bytes=total+len(nb)+len(ab)+len(root))
    if not a.no_upload:
        key = f"{a.work}/index.key"
        if not os.path.exists(key): subprocess.run(["ant", "pointer", "keygen", "-o", key], check=True, capture_output=True)
        pub["pointer"] = subprocess.run(["ant", "pointer", "address", "-k", key], capture_output=True, text=True).stdout.split()[-1]
        try: ant("pointer", "create", "-k", key, ra)
        except RuntimeError: ant("pointer", "update", "-k", key, ra)
    json.dump(pub, open(f"{out}/published.json", "w"), indent=1); print(json.dumps(pub, indent=1))

class Client:
    """Reads the published index the way an app would: pointer -> root -> shard (and names / adjacency on demand)."""
    def __init__(self, pointer, tmp): self.pointer, self.tmp, self.root, self.shards, self.calls, self.bytes, self.net = pointer, tmp, None, {}, 0, 0, 0.0; os.makedirs(tmp, exist_ok=True)
    def _get(self, addr, name):
        b, dt = get_chunk(addr, f"{self.tmp}/{name}"); self.calls += 1; self.bytes += len(b); self.net += dt; return b
    def load_root(self):
        t = time.time(); addr = ant("pointer", "resolve", self.pointer); self.calls += 1; self.net += time.time()-t
        self.root = parse_root(self._get(addr, "root"))
    def lookup(self, cell):
        if self.root is None: self.load_root()
        k = prefix_key(cell, self.root["res"])
        if k not in self.root["shards"]: return None
        if k not in self.shards: self.shards[k] = decode_shard(self._get(self.root["shards"][k], f"s{k}"))
        uuids, st, en, dv = self.shards[k]; o = ordinal(cell); i = bisect.bisect_right(st, o) - 1
        return fmt_uuid(uuids[dv[i]]) if i >= 0 and o <= en[i] else None

def cmd_verify(a):
    c = connect(a.work); cc = a.country; pub = json.load(open(f"{a.work}/out/published.json"))
    cl = Client(pub["pointer"], f"{a.work}/dl"); cl.load_root(); assert cl.root["shards"] and cl.root["country"] == cc
    ids, st, par, names = decode_names(cl._get(cl.root["names"], "names")); adj = decode_adjacency(cl._get(cl.root["adjacency"], "adj"), len(ids))
    print(f"root ok: release {cl.root['release']}, {len(cl.root['shards'])} shards; names {len(ids)} rows; adjacency {sum(map(len, adj))//2} edges")
    pts = c.sql(f"select lat, lon from '{a.points}' order by hash(id) limit {a.n}").fetchall() if a.points else []
    c.sql(f"CREATE OR REPLACE TEMP TABLE aa AS SELECT division_id id, ST_GeomFromText(wkt) g, ST_Area(ST_GeomFromText(wkt)) ar FROM '{a.work}/{cc}_area.parquet'")
    ok = none = n = 0; first = None
    for lat, lon in pts:
        cell = c.sql(f"select h3_latlng_to_cell({lat},{lon},9)").fetchone()[0]; t0 = time.time(); k0, b0 = cl.calls, cl.bytes
        got = cl.lookup(cell); dt = time.time()-t0
        if first is None: first = (cl.calls-k0, cl.bytes-b0, dt)
        truth = c.sql(f"select id from aa where ST_Contains(g, ST_Point({lon},{lat})) order by ar limit 1").fetchone(); truth = truth[0] if truth else None
        n += 1; ok += (got == truth); none += (got is None)
    if n: print(f"{n} points: exact vs smallest containing polygon {ok/n:.3f}; no answer {none/n:.3f}; cold lookup {first[0]} calls, {first[1]} B (after the names/adjacency fetch), {first[2]:.2f}s; total {cl.calls} calls, {cl.bytes/1e3:.0f} KB, {len(cl.shards)} shards")
    row = next(k for k, x in enumerate(st) if x == 'locality')
    def path(i):
        out = []
        while i >= 0: out.append(f"{names[i]} ({st[i]})"); i = par[i]
        return " > ".join(out)
    print("sample:", path(row), "| neighbours:", [names[j] for j in adj[row]][:6])

def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("cmd", choices=["extract", "cells", "adjacency", "build", "verify"])
    p.add_argument("--country", default="GB"); p.add_argument("--release", default=RELEASE)
    p.add_argument("--work", default=os.path.expanduser("~/Development/scratch/gers-index"))
    p.add_argument("--no-upload", action="store_true", help="build: encode only"); p.add_argument("--points", help="verify: parquet with id, lat, lon")
    p.add_argument("-n", type=int, default=400)
    a = p.parse_args(); os.makedirs(a.work, exist_ok=True); globals()["cmd_" + a.cmd](a)
if __name__ == "__main__": main()
