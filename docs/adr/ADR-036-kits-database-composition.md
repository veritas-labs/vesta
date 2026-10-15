# ADR-036: Database composition for the kits

**Status:** Accepted (decided unattended by the team lead on the user's standing decisions: separate repos, Space, services only),
2026-10-16. Reversible while nothing is published beyond Maven Local. Plan:
[kits-extraction-implementation-plan.md](../plans/kits-extraction-implementation-plan.md) (WP8, Phase B; WP9 and WP15 depend on
it). Related: [ADR-035](ADR-035-document-type-registry.md), [ADR-037](ADR-037-space-vocabulary.md),
[database-migrations.md](../testing/database-migrations.md).

## Context

The kits move out of `:shared` into a sibling repo (`vesta-kits`, Maven Local only). Every kit that touches SQLite today uses one
generated class, `VestaDatabase`, which appears in 42 production files and is opened by 56 test files. The facts that decide where
the schema lives (verified in `shared/src/commonMain/sqldelight`):

- All 21 `.sq` files are infrastructure. Vesta's own data lives in Automerge documents inside `AutomergeDocument`; Vesta's code
  reads only `AppSetting` directly. Every other `*Queries` is used by files that move into a kit.
- The schema is one linear, purely additive chain: 19 `.sqm` files, 20 checked-in snapshots, `verifyMigrations` on,
  `FullMigrationChainTest` (`CREATE TABLE IF NOT EXISTS`).
- Tables reference `Home(id)` with `ON DELETE CASCADE` (`AutomergeDocument`, `SyncEvent`, `HomeMember`, `InviteCode`,
  `AssistantGrant`; `AssistantGrant` also references `Assistant`). Others carry a bare `homeId` (`MailboxWrite`,
  `MailboxSequence`, `MailboxRead`, `MailboxSnapshot`, `BlobWant`, `JoinRequest`, `PendingJoin`, `PendingMessage`, `HomeGroup`,
  `AssistantActivity`). `Home.sq` queries join `HomeMember`.
- The query surface is small: 19 query sets used by 1-5 files each.

## Decision

**Option D: `store-kit` owns the entire existing schema and its migration chain, and generates the single database class that every
other kit and the app use. Vesta opens it and adds no tables.** The chain moves verbatim: no change to any table, column, index,
snapshot or `user_version` (20).

Options considered:

| Option | What it means | Verdict |
|---|---|---|
| A. A database per kit (`docs.db`, `spaces.db`, ...) | Each kit owns its tables and migrations | Rejected. Loses the cascade (`AutomergeDocument` and `SyncEvent` are docs-kit tables pointing at a spaces-kit table, an upward reference), loses the `Home`/`HomeMember` join, and needs a one-time data migration from `vesta.db` into several files on every existing install. A bad data migration destroys user data for good. |
| B. SQLDelight multi-module (`dependency(project(...))`) | Each kit has `.sq` files; the app's database composes them | Rejected for now. It is a same-build mechanism; across repos the schema would have to be published and read back, which could not be confirmed for SQLDelight 2.4.0. Migration numbering would also have to be shared across modules (one linear `user_version`), the same ownership problem in a harder form. |
| C. Storage ports (kits define interfaces, the app writes SQLDelight adapters) | Kits are database-agnostic | Rejected as the default. About 19 adapters in every consuming app, and a second app would copy Vesta's. Reasonable later if an app wants a non-SQLite store; nothing here prevents adding ports on top of `store-kit`. |
| **D. `store-kit` owns the whole schema** | One module, one generated class, the existing chain moved verbatim | **Chosen.** |

## Rules that follow

- **Per-app tables.** An app's own tables (Vesta has none) go in a second SQLDelight database in its own file, with no foreign keys
  into the kit schema. This is stated in the `store-kit` README so a second app does not extend the kit schema.
- **Ownership inside `store-kit`.** The `.sq` files stay flat; generated names such as `homeQueries` stay and become `internal` to
  the kits after extraction. A table-to-kit ownership table in the README records which kit's code may write which table, so the
  logical boundary is documented although the physical one is shared.
- **The database class.** It is renamed `VestaDatabase` -> `KitsDatabase` in WP9 (package `uk.veritaslabs.kits.store`); the app
  tells `DatabaseDriverFactory` where the file lives, with defaults reproducing today's paths exactly.

## Consequences

- No data migration and no change to any persisted byte; cascades and joins keep working; `verifyMigrations` and
  `FullMigrationChainTest` move with the code and keep proving the chain.
- Every database-using kit depends on `store-kit`, and a kit's tables are not independently versioned. This cost is already
  accepted: the kits live in one repo and version together.
- A second app gets the whole schema, including tables for kits it does not use.

## To verify, not assume (WP15 does so first)

1. SQLDelight-generated code compiles under Kotlin `explicitApi()`; if not, generated sources are excluded from the check, or the
   module opts out of explicit API for generated files only.
2. `generateAsync = true` and the `eygraber` androidx SQLite driver publish and resolve correctly from a KMP artifact.
3. `verifyMigrations` and the schema-generation task work in the new module with the existing `databases/*.db` snapshots. If a
   rename moves the snapshot folder, keep the folder and set `schemaOutputDirectory` to it; do not move snapshots.
4. WP15's deletion of Vesta's own copy of the schema waits for a human check against an old database.
