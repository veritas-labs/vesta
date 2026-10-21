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
