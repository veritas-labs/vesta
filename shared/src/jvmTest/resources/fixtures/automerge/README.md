# Automerge document fixtures

Checked-in Automerge binaries (`Document.save()` output) for the documents in
`shared/src/jvmAndroidMain/kotlin/uk/veritaslabs/vesta/automerge/`, as required by ADR-034 "Tests". They stand for what real
devices hold. The tests load each with the **current** code and assert what the readers return, that nothing is skipped,
that unknown keys are tolerated, and that opening through the document source does not damage the stored bytes.

- Code that made them: `AutomergeFixtures.kt` (`generators`), in `shared/src/jvmTest/kotlin/.../automerge/`.
- Tests: `AutomergeFixtureLoadTest.kt` (readers, migration behaviour) and `AutomergeFixtureSourceTest.kt` (open via
  `open*DocumentSource` / `CalendarSources` / `TaskSources` over an in-memory `VestaDatabase`).

## Rules

- A fixture is **immutable once released**. Never regenerate a shape that has shipped; add a new file for a new shape.
- Automerge bytes are not reproducible (random actor ids and change hashes), so tests assert content and heads, never bytes.
- ADR-034 names `shared/src/jvmAndroidTest/resources/fixtures/<module>/v<N>.automerge`. There is no `jvmAndroidTest` source
  set in this repository, so they live here, in `jvmTest`, flat, named `<type>-<shape>.automerge`.

## Regenerating (never automatic)

`RegenerateAutomergeFixtures` does nothing unless an environment variable is set:

    VESTA_REGENERATE_FIXTURES=1         ./gradlew :shared:jvmTest --tests '*RegenerateAutomergeFixtures*'   # only missing files
    VESTA_REGENERATE_FIXTURES=overwrite ./gradlew :shared:jvmTest --tests '*RegenerateAutomergeFixtures*'   # replaces existing ones

Adding a fixture: add an entry to `AutomergeFixtures.generators`, run with `=1`, then add its assertions.
`everyFixtureInTheDirectoryIsListedAndEveryListedFixtureExists` fails if the directory and the list disagree.

## How each shape was derived

"Current" fixtures are written with the production writers (`upsertPantryItem`, `addMerchant`, `putTask`, ...) at the commit
they were made, i.e. exactly what the shipped build writes. "Old" fixtures are written with the raw Automerge API, copying the
document structure from `git log -p` of the `*Document.kt` file at the commit named. "extra-keys" fixtures are the current
fixture plus keys a later version might write (a scalar `schemaVersion`, unknown fields on every entry, unknown map and list
values, unknown enum values, keys with an unknown slot or prefix); the readers must return the same data as for "current".

| File | Shape | Derived from |
|------|-------|--------------|
| `pantry-v1-no-registry` | `items` only; no `categories`, no `categoriesVersion`, no `merchantKeys` | commit 6260f05 (first Pantry), raw API |
| `pantry-categories-unversioned` | 15-primary registry + a custom category, **no** `categoriesVersion` | commits 52e04c5..886b9dc^ (ADR-010 registry before the marker), raw API |
| `pantry-categories-marker-1` | **Synthetic.** Short registry with `categoriesVersion = 1`. No release ever wrote 1 (the marker arrived as 2); included because ADR-034 asks for a version-1 registry | raw API |
| `pantry-current` | `categoriesVersion = 2`, 22 categories (2 custom), 4 items, with and without `merchantKeys` / secondary / barcode | production writers |
| `pantry-extra-keys` | `pantry-current` + unknown keys | `withExtras` |
| `shopping-list-v1-no-tags` | items without `tags`, `got`, `outOfStock` | commit 35a9157 (first shopping list), raw API |
| `shopping-list-current` | tags, partial / complete / out-of-stock progress | production writers |
| `shopping-list-extra-keys` | + unknown keys | `withExtras` |
| `merchants-current` | full merchant (hours, location, services), a bare one, an online one | production writers |
| `merchants-extra-keys` | + unknown fields/weekday, a merchant with an unknown type and service | `withExtras` |
| `cookbook-v1-staged-steps` | steps carry an `after` list; media are links with no blob | commits b8064b5/4b4596f, raw API |
| `cookbook-current` | blob + link media, ingredients with/without quantity, steps with/without minutes | production writers |
| `cookbook-extra-keys` | + unknown keys | `withExtras` |
| `mealplan-v1-no-eaters` | only `date|Slot|recipe` keys | commit 061d0eb (first Meal Planner), raw API |
| `mealplan-current` | + guests and chosen eaters | production writers |
| `mealplan-extra-keys` | + unknown slot, bad date, unknown prefix, scalar keys | `withExtras` |
| `calendar-current` | timed, all-day, repeating events | production writers |
| `calendar-extra-keys` | + unknown fields, unknown repeat value, an event with no start | `withExtras` |
| `tasks-current` | tasks with/without due, time, repeat, assignees; done records | production writers |
| `tasks-extra-keys` | + unknown keys | `withExtras` |
| `roster-v1-single-hub` | single `hub` scalar; no `devicename:`, no `gone:`, no `hub:` keys | commit 414d11f^, raw API |
| `roster-current` | members, devices, device names, unpaired device, several hubs, invites | production writers |
| `roster-extra-keys` | + unknown `member:` field, prefixes, invite field | `withExtras` |
| `home-registry-current` | live Homes and a tombstone | production writers |
| `home-registry-extra-keys` | + unknown keys | `withExtras` |
| `profile-registry-current` | named and placeholder profiles, with and without avatar | production writers |
| `profile-registry-extra-keys` | + unknown keys | `withExtras` |

No older-shape fixture was built for Merchants, Calendar, Tasks, Home registry or Profile registry: their git history shows
only fields being added (for example the Profile `named` flag in 26a00ad), and a document without such a field is the same
as a "current" entry that leaves it out. Pantry items without `merchantKeys` are in `pantry-current` for the same reason.
