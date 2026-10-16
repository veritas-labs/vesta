# Database migrations

The schema, its migrations and the checked-in snapshots moved to the `store-kit` module of the veritas-kits repository
(`~/Development/VeritasLabs/veritas-kits`, WP15 of the kits extraction plan). The full rules are in that repository's
`docs/database-migrations.md`. In short, a schema change is made there, in the same commit as a new `N.sqm` and a
regenerated `N+1.db` snapshot (`./gradlew :store-kit:generateCommonMainKitsDatabaseSchema`), and checked with
`./gradlew :store-kit:verifyCommonMainKitsDatabaseMigration`. Then publish the kit (`publishToMavenLocal`) and bump
`kits` in `gradle/libs.versions.toml`.

Vesta keeps the compatibility tests that run against the published artifact: `SchemaGuardTest` and
`ExistingInstallStillOpensTest` (with the version-20 fixture `vesta-v20.db`) in `shared/src/jvmTest/.../compat/`,
`AssistantMigrationTest` and `AgentCommandLogTest` in `.../vesta/db/`.
