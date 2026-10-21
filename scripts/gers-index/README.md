# GERS locality index on an Autonomi devnet (ADR-039)

Builds the locality-only index for one country from Overture Maps and publishes it as raw chunks plus a pointer on a **local
Autonomi devnet**. The devnet keeps its data in memory/temp state and **does not survive a reboot**, so everything here is
written to be re-run from scratch. Nothing here touches mainnet or a real wallet: the key is Anvil's public test account.

## What gets published

| Chunk | Contents |
|---|---|
| shards (about 13 for GB, up to ~190 KB each) | res-9 cell runs to a locality id, split by res-2 H3 prefix |
| names table (~0.58 MB GB, format `VGN2`) | every locality with a polygon, spatially ordered, plus its county/region/country ancestors: id, subtype, parent row, name, centroid lat/lon (integer millionths of a degree, delta-coded zigzag varints; ancestors have no polygon here so they are 0,0). The blob starts (after zlib) with the ASCII magic `VGN2`; `decode_names` rejects anything else. The root chunk is unchanged (`VGI2`) |
| adjacency (~0.12 MB GB) | touching localities per names-table row (nested localities excluded) |
| root (~0.5 KB) | `VGI2` magic, shard resolution, release, country, addresses of names + adjacency + each shard |
| **pointer** | owner-signed, mutable; points at the current root. The pointer *address* is what an app would ship with |

Formats are documented in the `encode_*` docstrings in `gers_index.py`. Chunk address = BLAKE3 of content.

## One-time setup

1. **Python.** `pip` on this machine points at a private index with expired credentials, so use `uv` and name PyPI:
   ```sh
   W=~/Development/scratch/gers-index; mkdir -p $W
   uv venv $W/.venv && uv pip install --python $W/.venv/bin/python --index-url https://pypi.org/simple duckdb==1.5.6
   ```
   DuckDB installs its `spatial`, `h3` (community) and `httpfs` extensions itself on first use (needs the network).
2. **`ant` CLI** 0.3.11 on PATH (`~/.local/bin/ant`).
3. **The devnet**, see below. Data and the devnet manifest live in `~/Development/scratch/ant-devnet-data` (override with
   `GERS_DEVNET_DIR`).

## Starting the devnet (needed after every reboot)

Binaries are built once; only the start step repeats. Memory file `reference_autonomi_local_devnet.md` has the full notes.

```sh
cd ~/Development/scratch/ant-client            # WithAutonomi/ant-client
# only if target/release/examples/start-local-devnet is missing:
OPENSSL_NO_VENDOR=1 cargo build --release --example start-local-devnet -p ant-core --features devnet

D=~/Development/scratch/ant-devnet-data        # contains bin/anvil (Foundry v1.8.5), not on PATH
cd $D && PATH=$D/bin:$PATH XDG_DATA_HOME=$D \
  nohup ~/Development/scratch/ant-client/target/release/examples/start-local-devnet > devnet.log 2>&1 &
```

Wait for `devnet.log` to say the network is up (25 nodes + a local Anvil chain), then check it:
`ant --devnet-manifest $D/ant/devnet-manifest.json --allow-loopback --evm-network local chunk put <some file>`.
`ant` global flags (`--devnet-manifest`, `--allow-loopback`, `--evm-network`) must come **before** the subcommand; the script
does this. Stop the devnet by PID (`pgrep -f '[s]tart-local-devnet'`), not `pkill -f`, which can match your own shell.
A restarted devnet starts empty, so a new run is needed; the old pointer and chunks are gone.

## Building and publishing

```sh
cd scripts/gers-index
P="$HOME/Development/scratch/gers-index/.venv/bin/python -I gers_index.py"
$P extract     # Overture S3 -> GB locality polygons + all GB divisions (a few minutes)
$P cells       # smallest locality per res-9 cell (a few minutes; ~12 GB RAM limit, spills to <work>/tmp)
$P adjacency   # touching localities (about 6 minutes for GB; nested pairs, overlap > 20%, are dropped)
$P build       # encode, upload chunks, create/update the pointer -> <work>/out/published.json
$P verify --points <work>/gb_places.parquet   # reads it back over the network and checks against polygons
```

`--country XX`, `--release`, `--work` and `--no-upload` (encode only, no devnet needed) are available. The extract, cells and
adjacency steps need no devnet, so they can run before it is started. Outputs live under `--work`
(default `~/Development/scratch/gers-index`). `out/published.json` records the root, names, adjacency and pointer addresses;
`index.key` is the pointer key (a throwaway devnet key; keep it so `build` can *update* the same pointer).

## Last run (2026-10-21, devnet, release 2026-09-23.1, GB, names format VGN2)

Same extract/cells/adjacency data as the first run (1.69M cells, 21,429 localities, 12 shards 0.90 MB), rebuilt with centroids:
names 22,093 rows (579 KB, was 466 KB), adjacency 61,355 edges (119 KB); 1.60 MB in total. `verify` (400 points): centroids of a
400-locality sample match `ST_Centroid` of the polygon within 1 microdegree (worst 0.50); all 162 ancestor rows are 0,0.

| | address |
|---|---|
| root (`VGI2`) | `926c6fc377dd9cddede73afcdd6e1622a30aef30561860c251dc3fe8950eff3f` |
| names (`VGN2`) | `91dd63b16a00b2a0d540978f3f0b1e012c2fc7db9c7996e80af5fd8c77da0728` |
| adjacency | `bbb50b6223990892fd84a9f228b4927912181f8af9ddbdbd1363d15a6173728d` |
| pointer (now at the new root) | `15e453fd3ea57732da45a5f108b34773865be44b5d349f7f111451b19bc1772d` |

The pre-VGN2 names chunk is superseded. Addresses are valid only on the devnet instance that was running.

## Places (ADR-039 places plan, WP-P1)

Businesses per locality, published as a second pack next to the locality index. **Devnet only**: every `ant` call goes through
`require_devnet()`, which exits unless `<devnet>/ant/devnet-manifest.json` exists, the command is pinned to it with
`--evm-network local` and the Anvil test key, and no real-wallet variable (`EVM_*`, `SECRET_KEY` other than Anvil's, ...) is set.
Only `id, name, lat, lon, confidence, category (taxonomy.primary), grp (taxonomy.hierarchy[0])` are ever extracted; a runtime
assertion and `test_places.py` pin that no phone/email/website/social/brand/address column is read into the data or encoded.

```sh
P="$HOME/Development/scratch/gers-index/.venv/bin/python -I gers_index.py"
$P places-extract        # Overture S3 -> <work>/places/GB_places_<release>.parquet (about 80 s)
$P places                # assign, encode, upload (4 parallel ant calls, retried), pointer -> out/places_published.json (about 8 min)
$P verify-places -n 300  # read 300 random localities back over the network, check every place
python -I test_places.py # format round trips, no-contact-column pin, devnet guard (no network needed)
```
`places --no-upload` encodes only (writes `out/places/*.bin`). `uploaded.json` there caches chunk addresses so a restart skips
finished uploads (`--fresh` forgets it). The pointer uses its own key, `<work>/places.key`; the locality pointer is untouched.

Assignment: smallest containing locality polygon wins ("inside"); otherwise the nearest polygon locality within 5 km, measured in
EPSG:27700 ("nearby"); otherwise left out and counted. Each locality's places are two groups, inside then nearby, each in H3 res 9
order, so a client can show both counts and fetch inside first. Nearby places are never described as "in" the locality.

| Chunk | Format | Contents |
|---|---|---|
| root | `VGP1`, raw, little-endian | magic, 16 B locality release, 2 B country, 32 B directory addr, 32 B category addr, u32 n, n x 32 B data chunk addrs |
| directory | `VGP2`, zlib | per data chunk compressed size; per names-table row, ranges (chunk, offset, length, count << 1 or nearby flag) |
| categories | `VGC1`, zlib | group names, category names, group index per category |
| data (381 for GB) | `VGD1`, zlib per chunk, 256 KiB max | units concatenated; a unit = one group of one locality (at most 2000 places), columnar: ids, zigzag-delta lat/lon microdegrees, category varints, confidence bytes, NUL names |

Byte layouts are in the docstrings of `encode_unit`, `encode_data_chunk`, `encode_places_dir`, `encode_categories`,
`encode_places_root`. Varints are LEB128, zigzag as for `VGN2`.

### Last run (2026-10-21, devnet, Overture 2026-09-23.1, GB)

Re-extracted from the pinned release (3,235,716 places, all named). Placed inside 1,649,461 (51.0%),
placed nearby 1,124,119 (34.7%), left out 462,136 (14.3%); 17,559 localities have places; 1,778 categories in 14 groups
(13 Overture groups plus `unknown`, the group of the 177,874 placed places with no taxonomy). 19,476 units, 381 data chunks, 89.8 MB (85.7 MiB, max 260,230 B),
directory 133 KB, categories 12.8 KB. Encode about 4.6 min; upload of 381 chunks + directory + categories 98 s (no retries needed).
`verify-places` (300 localities, 39,236 places): all match (id, name, position within 1 microdegree, category, group, confidence)
and the directory totals equal the published counts.

| | address |
|---|---|
| root (`VGP1`) | `99c58d969179a6a5b8f4542bb525280b4cfa15463def8d51f2cebda5295286e0` |
| directory (`VGP2`) | `675e97cabf045c7cc0c7e456c0481394d9cd21139c23545a0ac8eeff6488592c` |
| categories (`VGC1`) | `11a6368138157ed4231039dfd5917baa24e3a7d49754c90b39274d0644b0388e` |
| places pointer (`places.key`) | `b25531634e901256a8cc7f26076d12a87556fad956bcbd5044dcc01ee9f1cbaa` |

The root names locality release 2026-09-23.1 and rows of the names table in locality root `926c6fc3...`.

## Rebuilding after a devnet restart

The extracted parquet files and the `cells` table survive; only the network was lost. Re-run `build` (and `verify`). The
chunk addresses do not change, because they are hashes of unchanged content, so they are the same as before the reboot. A new
pointer *key* would give a new pointer address, so keep `index.key`.

## Known limits

- **Coverage.** Localities with a polygon cover only part of the land: on GB, about half of sampled places fall in none
  (checked 2026-10-21). Point-only localities are not indexed.
- **GB only so far.** Other countries use the same code but the top-down builder is not done: filling every res-9 cell of a
  large country needs far more memory (Australia reached 22 GB), so `cells` is only expected to work for small countries.
- Runs merge across absent cells, so a cell with no locality that sits inside a run can answer with a neighbour (about 0.2%).
- `verify` needs a parquet with `id, lat, lon` for ground-truth points; `gb_places.parquet` in the work dir works (positions
  only are used). No pointer API in the Android embed, so this is desktop (`ant` CLI) only.
