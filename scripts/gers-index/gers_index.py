#!/usr/bin/env python3
"""Build the locality-only GERS index for one country and publish it to an Autonomi devnet (ADR-039).

Subcommands (run in order; each is restartable):
  extract  Overture S3 -> <work>/<cc>_area.parquet (locality polygons) and <cc>_div.parquet (all divisions)
  cells    smallest locality per res-9 H3 cell -> table `cells` in <work>/work.duckdb
  adjacency  touching localities (nested ones excluded) -> <work>/<cc>_edges.parquet
  build    encode shards, names table, adjacency; upload chunks; set the pointer -> <work>/out/published.json
  verify   fetch everything back over the network and compare with point-in-polygon on sample points
  places-extract  Overture places (one country) -> <work>/places/<cc>_places_<release>.parquet (allowed columns only, see PLACES_COLUMNS)
  places   assign places to localities, encode VGD1/VGP2/VGC1/VGP1, upload to the local devnet -> <work>/out/places_published.json
  verify-places  fetch a random sample of localities back over the network and compare every place with the source

See README.md in this directory. Format notes are in the docstrings of the encode_* functions.
"""
import argparse, bisect, concurrent.futures, hashlib, json, os, random, re, struct, subprocess, sys, time, zlib
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
    require_devnet()
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

NAMES_MAGIC = b"VGN2"
def encode_names(rows, subtypes):
    """rows: [(uuid, subtype, parent_row_index_or_-1, name, lat_e6, lon_e6)] in table order (lat/lon: centroid in integer
    millionths of a degree, 0,0 for ancestor rows that have no polygon). Format VGN2:
    zlib( b"VGN2", varint nSub, nSub*(NUL-terminated name), varint n, n*16 B uuid, n subtype bytes,
          n*varint (parent index + 1, 0 = none), n*(NUL-terminated name),
          n*varint zigzag(dLat), n*varint zigzag(dLon) ), each delta against the previous row (first row against 0)."""
    sub = {s: i for i, s in enumerate(subtypes)}
    b = bytearray(NAMES_MAGIC) + varint(len(subtypes))
    for s in subtypes: b += s.encode() + b"\0"
    b += varint(len(rows))
    b += b"".join(bytes.fromhex(r[0].replace("-", "")) for r in rows)
    b += bytes(sub[r[1]] for r in rows)
    b += b"".join(varint(r[2]+1) for r in rows)
    b += b"".join((r[3] or "").encode() + b"\0" for r in rows)
    for k in (4, 5):
        prev = 0
        for r in rows: b += varint(zigzag(r[k]-prev)); prev = r[k]
    return zlib.compress(bytes(b), 9)

def decode_names(blob):
    b = zlib.decompress(blob)
    if b[:4] != NAMES_MAGIC: raise ValueError(f"unsupported names table: magic {bytes(b[:4])!r}, expected {NAMES_MAGIC!r} (rebuild with this version)")
    ns, i = read_varint(b, 4); subtypes = []
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
    cent = []
    for _ in range(2):
        col, prev = [], 0
        for _ in range(n): z, i = read_varint(b, i); prev += unzigzag(z); col.append(prev)
        cent.append(col)
    return ids, st, par, names, list(zip(*cent))   # centroids: [(lat_e6, lon_e6)] per row

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
    """Names-table rows: every locality with a polygon, spatially ordered (H3 res 6 of the centroid), then their ancestors.
    Each row ends with the centroid (lat, lon) in millionths of a degree; ancestors have no polygon here, so they get 0, 0."""
    rows = {r[0]: r for r in c.sql(f"select id, subtype, parent_division_id, name from '{work}/{cc}_div.parquet'").fetchall()}
    cen = {}; loc = []
    for u, la, lo in c.sql(f"""select division_id, ST_Y(ST_Centroid(ST_GeomFromText(wkt))) la, ST_X(ST_Centroid(ST_GeomFromText(wkt))) lo
        from '{work}/{cc}_area.parquet' order by h3_latlng_to_cell(la, lo, 6), division_id""").fetchall():
        loc.append(u); cen[u] = (round(la*1e6), round(lo*1e6))
    keep, seen = [], set(loc)
    for u in loc:
        x = rows[u][2]
        while x and x in rows and x not in seen: seen.add(x); keep.append(x); x = rows[x][2]
    order = loc + sorted(keep); ix = {u: i for i, u in enumerate(order)}
    return [(u, rows[u][1], ix.get(rows[u][2], -1), rows[u][3], *cen.get(u, (0, 0))) for u in order], ix

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
    ids, st, par, names, cent = decode_names(cl._get(cl.root["names"], "names")); adj = decode_adjacency(cl._get(cl.root["adjacency"], "adj"), len(ids))
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
    bad = 0; worst = 0; m = 0   # centroids: decoded vs ST_Centroid of the polygon, within 1 microdegree
    for u, la, lo in c.sql(f"select division_id, ST_Y(ST_Centroid(g)), ST_X(ST_Centroid(g)) from (select division_id, ST_GeomFromText(wkt) g from '{a.work}/{cc}_area.parquet') order by hash(division_id) limit {a.n}").fetchall():
        k = ids.index(u.replace("-", "")); m += 1; err = max(abs(cent[k][0]-la*1e6), abs(cent[k][1]-lo*1e6)); worst = max(worst, err); bad += err > 1
    assert bad == 0 and m, f"{bad} of {m} centroids differ by more than 1 microdegree"
    anc = [k for k, x in enumerate(st) if x != "locality"]
    print(f"centroids ok: {m} sampled localities within 1 microdegree (worst {worst:.2f}); {sum(cent[k] == (0, 0) for k in anc)} of {len(anc)} ancestor rows are 0,0")
    row = next(k for k, x in enumerate(st) if x == 'locality')
    def path(i):
        out = []
        while i >= 0: out.append(f"{names[i]} ({st[i]})"); i = par[i]
        return " > ".join(out)
    print("sample:", path(row), "| centroid:", cent[row], "| neighbours:", [names[j] for j in adj[row]][:6])

# ---- places: formats (ADR-039 places plan, WP-P1) ------------------------------------------------------------------
# All integers are little-endian where fixed width ("<"), and LEB128 varints (7 bits per byte, least-significant group
# first, high bit = more bytes follow) elsewhere. zigzag(d) = 2d for d >= 0, -2d-1 for d < 0. Strings are UTF-8, NUL-terminated.
# Every blob below is zlib (RFC 1950, as Python zlib.compress / java.util.zip.Inflater) except the root, which is raw.
PLACES_S3 = "s3://overturemaps-us-west-2/release/{rel}/theme=places/type=place/*.parquet"
PLACES_BBOX = {"GB": (-9.0, 2.1, 49.8, 61.0)}   # xmin, xmax, ymin, ymax: only a row-group pruning hint, the country filter decides
NEAR_CRS = {"GB": "EPSG:27700"}                  # metric CRS for the nearby-distance rule; add one per country
NEAR_CAP_M = 5000                                # a place outside every polygon joins the nearest polygon locality within this
CHUNK_TARGET = 256 * 1024                        # compressed bytes per data chunk (upper bound)
MAX_UNIT_PLACES = 2000                           # a locality's group is split into units of at most this many places
# The ONLY columns that are ever extracted, held or encoded. name = Overture names.primary; category = taxonomy.primary;
# grp = taxonomy.hierarchy[0] (DuckDB: [1]). No phone, email, website, social, brand or address column, by construction.
PLACES_EXTRACT = (("id", "id"), ("name", "names.primary"), ("lat", "ST_Y(geometry)"), ("lon", "ST_X(geometry)"),
                  ("confidence", "confidence"), ("category", "taxonomy.primary"), ("grp", "taxonomy.hierarchy[1]"))
PLACES_COLUMNS = tuple(a for a, _ in PLACES_EXTRACT)
CONTACT_WORDS = re.compile(r"phone|email|website|social|address|brand|contact|freeform|postcode|url|link", re.I)

def assert_no_contact_columns(cols):
    """Runtime pin: refuses any column that is not in PLACES_COLUMNS or that looks like contact/address data."""
    bad = [c for c in cols if c not in PLACES_COLUMNS or CONTACT_WORDS.search(c)]
    if bad: raise AssertionError(f"places data must only carry {PLACES_COLUMNS}; refusing columns {bad}")
    return list(cols)

def extract_select():
    """The SELECT list of places-extract, built from PLACES_EXTRACT (the address column is read only in the WHERE filter)."""
    assert_no_contact_columns(PLACES_COLUMNS)
    return ", ".join(f"{e} AS {a}" for a, e in PLACES_EXTRACT)

PLACES_MAGIC = b"VGD1"
def encode_unit(pl, ci):
    """One unit = the places of one group (inside or nearby) of one locality, at most MAX_UNIT_PLACES, in H3 order.
    pl: [(id uuid string, name, lat degrees, lon degrees, confidence 0..1 or None, category name)]; ci: category name -> index.
    Layout (all columns have n entries): varint n, n*16 B uuid, n*varint zigzag(dLat), n*varint zigzag(dLon) (microdegrees,
    round(deg*1e6), each delta against the previous place of the unit, the first against 0), n*varint category index
    (into the category table), n*1 B confidence = round(conf*255) (0 if absent), n*(NUL-terminated name)."""
    b = bytearray(varint(len(pl)))
    b += b"".join(bytes.fromhex(r[0].replace("-", "")) for r in pl)
    for k in (2, 3):
        prev = 0
        for r in pl: v = round(r[k] * 1e6); b += varint(zigzag(v - prev)); prev = v
    for r in pl: b += varint(ci[r[5]])
    b += bytes(min(255, max(0, round((r[4] or 0) * 255))) for r in pl)
    for r in pl: b += (r[1] or "").replace("\0", " ").encode() + b"\0"
    return bytes(b)

def decode_unit(b, i):
    """-> ([(uuid hex without dashes, name, lat_e6, lon_e6, confidence byte, category index)], index after the unit)."""
    n, i = read_varint(b, i)
    ids = [b[i+16*k: i+16*k+16].hex() for k in range(n)]; i += 16*n
    cols = []
    for _ in range(2):
        col, prev = [], 0
        for _ in range(n): z, i = read_varint(b, i); prev += unzigzag(z); col.append(prev)
        cols.append(col)
    cat = []
    for _ in range(n): c, i = read_varint(b, i); cat.append(c)
    conf = list(b[i:i+n]); i += n
    names = []
    for _ in range(n): j = b.index(0, i); names.append(bytes(b[i:j]).decode()); i = j+1
    return list(zip(ids, names, cols[0], cols[1], conf, cat)), i

def encode_data_chunk(units):
    """VGD1 data chunk = zlib( b"VGD1", unit, unit, ... ). Units are concatenated with no framing: the directory (VGP2) gives
    each range's offset and length in the DECOMPRESSED bytes (offsets count the 4 magic bytes, so the first is 4)."""
    return zlib.compress(PLACES_MAGIC + b"".join(units), 9)
def decode_data_chunk(blob):
    b = zlib.decompress(blob)
    if b[:4] != PLACES_MAGIC: raise ValueError(f"unsupported places chunk: magic {bytes(b[:4])!r}, expected {PLACES_MAGIC!r}")
    return b

DIR_MAGIC = b"VGP2"
def encode_places_dir(chunk_sizes, ranges, nrows):
    """VGP2 directory. zlib( b"VGP2", varint nChunks, nChunks*varint compressed size of that data chunk in bytes (what a
    fetch downloads; chunk i is the i-th address of the root), varint nRows (= rows of the VGN2 names table, same order),
    then for each names-table row: varint nRanges, nRanges*( varint chunkIndex, varint offset, varint length,
    varint (count << 1 | nearby) ) ).
    A range is one unit: bytes [offset, offset+length) of the decompressed chunk, holding `count` places. The low bit says
    which group the range holds: 0 = inside (the place is inside the locality polygon), 1 = nearby (outside every polygon,
    this locality is the nearest within NEAR_CAP_M; NEVER describe these as "in" the locality). Per row all inside ranges come
    first, then all nearby ranges; within a group the places run in H3 (res 9) order across the ranges, so a prefix of the
    ranges is a spatially compact subset. A row with no places is one 0 byte. Count of a group = sum of its ranges' counts.
    ranges: {row: [(chunk, offset, length, count, nearby)]}."""
    b = bytearray(DIR_MAGIC) + varint(len(chunk_sizes))
    for s in chunk_sizes: b += varint(s)
    b += varint(nrows)
    for r in range(nrows):
        rs = ranges.get(r, []); b += varint(len(rs))
        for ch, off, ln, cnt, nb in rs: b += varint(ch) + varint(off) + varint(ln) + varint(cnt << 1 | nb)
    return zlib.compress(bytes(b), 9)

def decode_places_dir(blob):
    """-> (chunk_sizes, [per row: [(chunk, offset, length, count, nearby)]])."""
    b = zlib.decompress(blob)
    if b[:4] != DIR_MAGIC: raise ValueError(f"unsupported places directory: magic {bytes(b[:4])!r}, expected {DIR_MAGIC!r}")
    nc, i = read_varint(b, 4); sizes = []
    for _ in range(nc): s, i = read_varint(b, i); sizes.append(s)
    n, i = read_varint(b, i); rows = []
    for _ in range(n):
        k, i = read_varint(b, i); rs = []
        for _ in range(k):
            ch, i = read_varint(b, i); off, i = read_varint(b, i); ln, i = read_varint(b, i); cn, i = read_varint(b, i)
            rs.append((ch, off, ln, cn >> 1, cn & 1))
        rows.append(rs)
    return sizes, rows

CAT_MAGIC = b"VGC1"
def encode_categories(groups, cats):
    """VGC1 category table. zlib( b"VGC1", varint nGroups, nGroups*(NUL-terminated group name), varint nCats,
    nCats*(NUL-terminated category name), nCats*varint group index ). Both lists are ordered by place count, most
    common first. groups = top-level taxonomy groups (about 13); cats = [(category name = taxonomy.primary, group name)].
    A place's category varint indexes `cats`; its group is cats[i].group."""
    gi = {g: i for i, g in enumerate(groups)}
    b = bytearray(CAT_MAGIC) + varint(len(groups))
    for g in groups: b += g.encode() + b"\0"
    b += varint(len(cats))
    for c, _ in cats: b += c.encode() + b"\0"
    for _, g in cats: b += varint(gi[g])
    return zlib.compress(bytes(b), 9)

def decode_categories(blob):
    """-> (groups [name], cats [(category name, group index)])."""
    b = zlib.decompress(blob)
    if b[:4] != CAT_MAGIC: raise ValueError(f"unsupported category table: magic {bytes(b[:4])!r}, expected {CAT_MAGIC!r}")
    ng, i = read_varint(b, 4); groups = []
    for _ in range(ng): j = b.index(0, i); groups.append(b[i:j].decode()); i = j+1
    nc, i = read_varint(b, i); names = []
    for _ in range(nc): j = b.index(0, i); names.append(b[i:j].decode()); i = j+1
    cats = []
    for k in range(nc): g, i = read_varint(b, i); cats.append((names[k], g))
    return groups, cats

PLACES_ROOT_HDR = struct.Struct("<4s16s2s32s32sI")
def encode_places_root(locality_release, country, dir_addr, cat_addr, chunk_addrs):
    """VGP1 root, NOT compressed, little-endian: 4 B magic b"VGP1", 16 B ASCII locality release (the release of the VGI2/VGN2
    pack the directory rows refer to, space-padded), 2 B ASCII country, 32 B directory address (VGP2), 32 B category table
    address (VGC1), u32 chunk count, then chunk count * 32 B data chunk addresses (VGD1), index = chunkIndex of the directory.
    Addresses are raw 32-byte chunk addresses (hex in the JSON files)."""
    r = PLACES_ROOT_HDR.pack(b"VGP1", locality_release.encode().ljust(16), country.encode(), bytes.fromhex(dir_addr), bytes.fromhex(cat_addr), len(chunk_addrs))
    return r + b"".join(bytes.fromhex(a) for a in chunk_addrs)
def parse_places_root(b):
    magic, rel, cc, da, ca, n = PLACES_ROOT_HDR.unpack_from(b)
    if magic != b"VGP1": raise ValueError(f"unsupported places root: magic {magic!r}, expected b'VGP1'")
    o = PLACES_ROOT_HDR.size
    return dict(release=rel.decode().strip(), country=cc.decode(), directory=da.hex(), categories=ca.hex(), chunks=[b[o+32*k: o+32*k+32].hex() for k in range(n)])

def pack_chunks(units, target=CHUNK_TARGET):
    """units: [blob] in packing order. Greedy: each chunk takes consecutive units while the raw size fits a budget derived
    from the running compression ratio, then is compressed and shrunk (units handed back to the next chunk) until the zlib
    result is <= target. A unit is never split. -> (chunks [compressed bytes], where [(chunk, offset, length)] per unit)."""
    n = len(units); chunks = []; where = [None]*n; i = 0; ratio = 0.75
    while i < n:
        budget = int(target / ratio * 0.97); j = i; raw = 4
        while j < n and (j == i or raw + len(units[j]) <= budget): raw += len(units[j]); j += 1
        while True:
            body = PLACES_MAGIC + b"".join(units[i:j]); z = zlib.compress(body, 9)
            if len(z) <= target or j - i == 1: break
            want = (len(body) - 4) * target / len(z) * 0.97; raw = 0; k = i
            while k < j - 1 and raw + len(units[k]) <= want: raw += len(units[k]); k += 1
            j = max(i + 1, k)
        off = 4
        for u in range(i, j): where[u] = (len(chunks), off, len(units[u])); off += len(units[u])
        chunks.append(z); ratio = 0.5*ratio + 0.5*len(z)/len(body); i = j
    return chunks, where

# ---- places: safety, upload ----------------------------------------------------------------------------------------
ANVIL_KEY = "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80"
REAL_WALLET_ENV = ("EVM_NETWORK", "EVM_PRIVATE_KEY", "EVM_RPC_URL", "EVM_PAYMENT_TOKEN_ADDRESS", "EVM_DATA_PAYMENTS_ADDRESS",
                   "ANT_PRIVATE_KEY", "ANT_EVM_NETWORK", "AUTONOMI_PRIVATE_KEY", "WALLET_PRIVATE_KEY")
def require_devnet():
    """Refuses (SystemExit) unless the local devnet manifest exists and every ant call is pinned to it with the Anvil test key
    and `--evm-network local`, and no real-wallet / network override is set in the environment."""
    man = f"{DEVNET}/ant/devnet-manifest.json"
    if not os.path.isfile(man): sys.exit(f"refusing to run: devnet manifest {man} not found (places only go to the local devnet)")
    if ANT[ANT.index("--devnet-manifest") + 1] != man or ANT[ANT.index("--evm-network") + 1] != "local" or ENV.get("SECRET_KEY") != ANVIL_KEY:
        sys.exit("refusing to run: the ant command is not pinned to the local devnet with the Anvil test key")
    bad = [k for k in REAL_WALLET_ENV if os.environ.get(k)] + (["SECRET_KEY"] if os.environ.get("SECRET_KEY") not in (None, "", ANVIL_KEY) else [])
    if bad: sys.exit(f"refusing to run: real-wallet or network settings in the environment: {bad}")

def put_retry(path, tries=5):
    for k in range(tries):
        try: return put_chunk(path)
        except RuntimeError:
            if k == tries - 1: raise
            time.sleep(2 ** k)

def set_pointer(key, addr):
    if not os.path.exists(key): subprocess.run(["ant", "pointer", "keygen", "-o", key], check=True, capture_output=True)
    pa = subprocess.run(["ant", "pointer", "address", "-k", key], capture_output=True, text=True).stdout.split()[-1]
    try: ant("pointer", "create", "-k", key, addr)
    except RuntimeError: ant("pointer", "update", "-k", key, addr)
    return pa

# ---- places: subcommands -------------------------------------------------------------------------------------------
def places_src(a): return f"{a.work}/places/{a.country}_places_{a.release}.parquet"
def places_assigned(a): return f"{a.work}/places/{a.country}_assigned_{a.release}.parquet"

def cmd_places_extract(a):
    """Overture places -> parquet with PLACES_COLUMNS only. `addresses` is read in the WHERE (country) and never selected."""
    c = connect(a.work, ("spatial",)); c.sql("INSTALL httpfs; LOAD httpfs; SET s3_region='us-west-2'"); t = time.time()
    os.makedirs(f"{a.work}/places", exist_ok=True); cc = a.country
    bb = PLACES_BBOX.get(cc); box = f" AND bbox.xmin BETWEEN {bb[0]} AND {bb[1]} AND bbox.ymin BETWEEN {bb[2]} AND {bb[3]}" if bb else ""
    c.sql(f"""COPY (SELECT {extract_select()} FROM read_parquet('{PLACES_S3.format(rel=a.release)}')
        WHERE addresses[1].country = '{cc}'{box}) TO '{places_src(a)}' (FORMAT parquet, COMPRESSION zstd)""")
    cols = [r[0] for r in c.sql(f"describe select * from '{places_src(a)}'").fetchall()]
    assert cols == list(PLACES_COLUMNS), cols; assert_no_contact_columns(cols)
    print("places", c.sql(f"select count(*) from '{places_src(a)}'").fetchone()[0], "columns", cols, f"{time.time()-t:.0f}s")

def assign(c, a, rowmap):
    """Writes <work>/places/<cc>_assigned_<release>.parquet: PLACES_COLUMNS + loc (names-table row) + nearby (0/1) + h9, for
    every place with a name that is placed. Smallest containing polygon wins (area in degrees^2, ties by id); otherwise the
    nearest polygon locality within NEAR_CAP_M (distance in metres in NEAR_CRS); otherwise left out. -> counts dict."""
    t = time.time(); cc = a.country; crs = NEAR_CRS.get(cc) or sys.exit(f"no metric CRS for {cc}: add it to NEAR_CRS")
    src = places_src(a); assert_no_contact_columns([r[0] for r in c.sql(f"describe select * from '{src}'").fetchall()])
    c.sql("CREATE OR REPLACE TABLE rowmap(div VARCHAR, loc INTEGER)")
    c.executemany("INSERT INTO rowmap VALUES (?, ?)", list(rowmap.items()))
    c.sql(f"CREATE OR REPLACE TABLE ar AS SELECT division_id, ST_GeomFromText(wkt) g FROM '{a.work}/{cc}_area.parquet'")
    c.sql("ALTER TABLE ar ADD COLUMN ar DOUBLE; UPDATE ar SET ar = ST_Area(g)")
    c.sql(f"""CREATE OR REPLACE TABLE p AS SELECT row_number() OVER () AS rn, id, replace(trim("name"), chr(0), ' ') AS "name", lat, lon, confidence,
        coalesce(category, 'unknown') AS category, coalesce(grp, 'unknown') AS grp, ST_Point(lon, lat) pt FROM '{src}'""")
    total = c.sql("select count(*) from p").fetchone()[0]
    c.sql("DELETE FROM p WHERE name IS NULL OR name = ''"); kept = c.sql("select count(*) from p").fetchone()[0]
    c.sql("""CREATE OR REPLACE TABLE inside AS SELECT p.rn, arg_min(ar.division_id, (ar.ar, ar.division_id)) div
             FROM p JOIN ar ON ST_Intersects(ar.g, p.pt) GROUP BY p.rn""")
    n_in = c.sql("select count(*) from inside").fetchone()[0]; print("inside", n_in, f"{time.time()-t:.0f}s", flush=True)
    c.sql(f"CREATE OR REPLACE TABLE ax AS SELECT division_id, ST_Transform(g,'EPSG:4326','{crs}',always_xy:=true) g FROM ar")
    c.sql("CREATE OR REPLACE TABLE ab AS SELECT division_id, g, ST_XMin(g) x0, ST_XMax(g) x1, ST_YMin(g) y0, ST_YMax(g) y1 FROM ax")
    c.sql(f"""CREATE OR REPLACE TABLE rest AS SELECT rn, ST_X(m) x, ST_Y(m) y FROM (SELECT rn, ST_Transform(pt,'EPSG:4326','{crs}',always_xy:=true) m FROM p
              WHERE rn NOT IN (SELECT rn FROM inside))""")
    n = c.sql("select count(*) from rest").fetchone()[0]
    c.sql("CREATE OR REPLACE TABLE near AS SELECT * FROM (SELECT NULL::BIGINT rn, NULL::VARCHAR div, NULL::DOUBLE d) WHERE false"); step = 200000
    for lo in range(0, n, step):
        c.sql(f"""INSERT INTO near SELECT rn, arg_min(division_id, (d, division_id)), min(d) FROM (
            SELECT r.rn, b.division_id, ST_Distance(b.g, ST_Point(r.x, r.y)) d FROM (SELECT * FROM rest ORDER BY rn LIMIT {step} OFFSET {lo}) r
            JOIN ab b ON r.x BETWEEN b.x0-{NEAR_CAP_M} AND b.x1+{NEAR_CAP_M} AND r.y BETWEEN b.y0-{NEAR_CAP_M} AND b.y1+{NEAR_CAP_M}) WHERE d <= {NEAR_CAP_M} GROUP BY rn""")
        print("  nearby batch", lo, f"{time.time()-t:.0f}s", flush=True)
    n_near = c.sql("select count(*) from near").fetchone()[0]
    cols = ", ".join(f"p.{x}" for x in PLACES_COLUMNS)
    c.sql(f"""COPY (SELECT {cols}, rowmap.loc AS loc, CASE WHEN i.rn IS NULL THEN 1 ELSE 0 END AS nearby, h3_latlng_to_cell(p.lat, p.lon, 9) AS h9
        FROM p LEFT JOIN inside i ON i.rn = p.rn LEFT JOIN near n ON n.rn = p.rn JOIN rowmap ON rowmap.div = coalesce(i.div, n.div))
        TO '{places_assigned(a)}' (FORMAT parquet, COMPRESSION zstd)""")
    counts = dict(total=total, no_name=total-kept, inside=n_in, nearby=n_near, left_out=kept-n_in-n_near)
    assert counts["no_name"] + n_in + n_near + counts["left_out"] == total
    print("assign", counts, f"{time.time()-t:.0f}s"); return counts

def cmd_places(a):
    if not a.no_upload: require_devnet()
    c = connect(a.work); out = f"{a.work}/out"; pd = f"{out}/places"; os.makedirs(pd, exist_ok=True); cc = a.country; t0 = time.time()
    ids = decode_names(open(f"{out}/names.bin", "rb").read())[0]; nrows = len(ids)
    pub0 = json.load(open(f"{out}/published.json")); assert pub0["country"] == cc, "out/published.json is for another country"
    counts = assign(c, a, {fmt_uuid(h): i for i, h in enumerate(ids)})
    cats = c.sql(f"select category, mode(grp), count(*) from '{places_assigned(a)}' group by 1 order by 3 desc, 1").fetchall()
    groups = [r[0] for r in c.sql(f"select grp, count(*) from '{places_assigned(a)}' group by 1 order by 2 desc, 1").fetchall()]
    ci = {r[0]: i for i, r in enumerate(cats)}
    cb = encode_categories(groups, [(r[0], r[1]) for r in cats]); open(f"{pd}/categories.bin", "wb").write(cb)
    q = c.sql(f"select loc, nearby, id, name, lat, lon, confidence, category from '{places_assigned(a)}' order by loc, nearby, h9, id")
    blobs, meta = [], []; cur = None; buf = []
    def flush():
        for k in range(0, len(buf), MAX_UNIT_PLACES):
            part = buf[k:k+MAX_UNIT_PLACES]; blobs.append(encode_unit(part, ci)); meta.append((cur[0], cur[1], len(part)))
    while True:
        rows = q.fetchmany(200000)
        if not rows: break
        for r in rows:
            if (r[0], r[1]) != cur: flush(); cur = (r[0], r[1]); buf = []
            buf.append(r[2:])
    flush()
    chunks, where = pack_chunks(blobs)
    ranges = {}
    for (row, nb, cnt), (ch, off, ln) in zip(meta, where): ranges.setdefault(row, []).append((ch, off, ln, cnt, nb))
    db = encode_places_dir([len(z) for z in chunks], ranges, nrows); open(f"{pd}/directory.bin", "wb").write(db)
    for f in os.listdir(pd):
        if f.startswith("chunk_"): os.remove(f"{pd}/{f}")
    paths = []
    for k, z in enumerate(chunks): p = f"{pd}/chunk_{k:04d}.bin"; open(p, "wb").write(z); paths.append(p)
    tot = sum(map(len, chunks)); print(f"{len(chunks)} chunks, {tot} B ({tot/2**20:.1f} MiB), max {max(map(len, chunks))}; directory {len(db)} B; categories {len(cb)} B ({len(cats)} categories, {len(groups)} groups); {len(ranges)} localities with places; encode {time.time()-t0:.0f}s", flush=True)
    counts.update(localities_with_places=len(ranges), categories=len(cats), groups=len(groups), units=len(blobs))
    up_s = 0.0
    if a.no_upload: addrs = ["0"*64]*len(chunks); da = ca = "0"*64
    else:
        cache_p = f"{pd}/uploaded.json"; done = {} if a.fresh or not os.path.exists(cache_p) else json.load(open(cache_p)); t = time.time()
        def put(p):
            h = hashlib.sha256(open(p, "rb").read()).hexdigest()
            if h not in done: done[h] = put_retry(p)
            return done[h]
        addrs = [None]*len(paths); failed = []
        with concurrent.futures.ThreadPoolExecutor(a.workers) as ex:
            futs = {ex.submit(put, p): k for k, p in enumerate(paths)}
            for n, f in enumerate(concurrent.futures.as_completed(futs), 1):
                try: addrs[futs[f]] = f.result()
                except Exception as e: failed.append(futs[f]); print("  failed", futs[f], str(e)[-200:], flush=True)
                if n % 25 == 0: print(f"  uploaded {n}/{len(paths)} {time.time()-t:.0f}s", flush=True); json.dump(done, open(cache_p, "w"))
        for k in failed: addrs[k] = put(paths[k])    # one at a time, with retries; raises if it still fails
        da, ca = put(f"{pd}/directory.bin"), put(f"{pd}/categories.bin"); json.dump(done, open(cache_p, "w")); up_s = time.time() - t
        print(f"uploaded {len(paths)} chunks + directory + categories in {up_s:.0f}s ({len(failed)} needed a serial retry)")
    root = encode_places_root(pub0["release"], cc, da, ca, addrs); open(f"{pd}/root.bin", "wb").write(root)
    ra = "0"*64 if a.no_upload else put_retry(f"{pd}/root.bin")
    pub = dict(country=cc, release=a.release, locality_release=pub0["release"], locality_root=pub0["root"], root=ra, directory=da, categories=ca,
               chunk_count=len(chunks), chunks=addrs, counts=counts, bytes=tot + len(db) + len(cb) + len(root), upload_seconds=round(up_s, 1), near_cap_m=NEAR_CAP_M)
    if not a.no_upload:
        try: pub["pointer"] = set_pointer(f"{a.work}/places.key", ra)
        except Exception as e: print("pointer not placed:", str(e)[-200:])
    json.dump(pub, open(f"{out}/places_published.json", "w"), indent=1); print(json.dumps({k: v for k, v in pub.items() if k != "chunks"}, indent=1))

def cmd_verify_places(a):
    """Reads the places pack back over the network (root -> directory, categories -> chunks) for a random sample of localities and
    checks every place against the assigned source: id set per group, name, position within 1 microdegree, category, group,
    confidence; inside/nearby counts and ordering against the directory; whole-directory totals against places_published.json."""
    require_devnet(); c = connect(a.work); out = f"{a.work}/out"; pub = json.load(open(f"{out}/places_published.json")); tmp = f"{a.work}/dl_places"; os.makedirs(tmp, exist_ok=True)
    if pub.get("pointer"): assert ant("pointer", "resolve", pub["pointer"]) == pub["root"], "pointer does not resolve to the root"
    root = parse_places_root(get_chunk(pub["root"], f"{tmp}/root")[0]); assert root["chunks"] == pub["chunks"] and root["country"] == a.country
    sizes, drows = decode_places_dir(get_chunk(root["directory"], f"{tmp}/dir")[0]); groups, cats = decode_categories(get_chunk(root["categories"], f"{tmp}/cat")[0])
    assert len(sizes) == len(root["chunks"]) and len(drows) == len(decode_names(open(f"{out}/names.bin", "rb").read())[0])
    tin = sum(r[3] for rs in drows for r in rs if not r[4]); tn = sum(r[3] for rs in drows for r in rs if r[4])
    assert (tin, tn) == (pub["counts"]["inside"], pub["counts"]["nearby"]), (tin, tn, pub["counts"])
    print(f"root ok: release {root['release']}, {len(root['chunks'])} chunks; directory totals inside {tin} nearby {tn}; {len(cats)} categories in {len(groups)} groups")
    have = [i for i, rs in enumerate(drows) if rs]; sample = random.Random(a.seed).sample(have, min(a.n, len(have))); need = sorted({r[0] for i in sample for r in drows[i]})
    cache = {}; t = time.time()
    def fetch(k): return k, decode_data_chunk(get_chunk(root["chunks"][k], f"{tmp}/c{k}")[0])
    with concurrent.futures.ThreadPoolExecutor(a.workers) as ex:
        for k, raw in ex.map(fetch, need): cache[k] = raw
    print(f"fetched {len(need)} chunks, {sum(sizes[k] for k in need)/1e6:.1f} MB compressed, {time.time()-t:.0f}s")
    want = {}
    for r in c.sql(f"select loc, nearby, id, name, lat, lon, confidence, category, grp from '{places_assigned(a)}' where loc in ({','.join(map(str, sample))})").fetchall():
        want.setdefault((r[0], r[1]), {})[r[2].replace("-", "")] = r[3:]
    checked = 0
    for row in sample:
        seen_nearby = False; got = {0: {}, 1: {}}; cnt = {0: 0, 1: 0}
        for ch, off, ln, cn, nb in drows[row]:
            assert not (seen_nearby and not nb), f"row {row}: inside range after a nearby range"; seen_nearby |= bool(nb)
            pl, end = decode_unit(cache[ch], off); assert end == off + ln and len(pl) == cn, (row, ch, off, ln, end, len(pl), cn)
            cnt[nb] += cn
            for pid, name, la, lo, conf, cat in pl: assert pid not in got[nb]; got[nb][pid] = (name, la, lo, conf, cat)
        for nb in (0, 1):
            w = want.get((row, nb), {}); assert set(w) == set(got[nb]) and cnt[nb] == len(w), f"row {row} group {nb}: {len(got[nb])} read, {len(w)} expected"
            for pid, (name, la, lo, conf, cat) in got[nb].items():
                wn, wla, wlo, wc, wcat, wgrp = w[pid]
                assert name == wn and cats[cat][0] == wcat and groups[cats[cat][1]] == wgrp, (pid, name, wn, cats[cat], wcat, wgrp)
                assert abs(la - wla*1e6) <= 1 and abs(lo - wlo*1e6) <= 1, (pid, la, wla, lo, wlo)
                assert conf == min(255, max(0, round((wc or 0)*255))), (pid, conf, wc); checked += 1
    print(f"verify-places ok: {len(sample)} localities, {checked} places match the source (id, name, position within 1 microdegree, category, group, confidence); inside/nearby counts match the directory")

def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("cmd", choices=["extract", "cells", "adjacency", "build", "verify", "places-extract", "places", "verify-places"])
    p.add_argument("--country", default="GB"); p.add_argument("--release", default=RELEASE)
    p.add_argument("--work", default=os.path.expanduser("~/Development/scratch/gers-index"))
    p.add_argument("--no-upload", action="store_true", help="build: encode only"); p.add_argument("--points", help="verify: parquet with id, lat, lon")
    p.add_argument("-n", type=int, default=400, help="verify: points; verify-places: localities (use 300)")
    p.add_argument("--workers", type=int, default=4, help="places, verify-places: parallel ant calls"); p.add_argument("--fresh", action="store_true", help="places: forget which chunks were already uploaded")
    p.add_argument("--seed", type=int, default=1, help="verify-places: sample seed")
    a = p.parse_args(); os.makedirs(a.work, exist_ok=True); globals()["cmd_" + a.cmd.replace("-", "_")](a)
if __name__ == "__main__": main()
