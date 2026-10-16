# Vesta — Core Entry Experience Spec

> First-run, Profiles, create-or-join a Home, and the "in a Home / not yet in a Home" states.
> This is the foundation layer. The tools (Pantry, Shopping List and the rest) sit on top of it.

---

## 1. First Launch / Entry

### What a brand-new user sees

**Screen 1: Welcome**

A single screen. Vesta logo. One sentence explaining what Vesta is. One primary button: **Get Started**. No sign-up
form, no email field, no account creation. The welcome screen establishes trust: this app does not require anything from
you yet.

| Element       | Detail                                                                                        |
|---------------|-----------------------------------------------------------------------------------------------|
| Heading       | "Welcome to Vesta"                                                                            |
| Subtext       | "Your household, your rules. No account needed."                                              |
| Button        | **Get Started** → advances to Profile Setup                                                   |
| Accessibility | Minimum 16sp body text. High-contrast by default. Screen reader announces heading and button. |

### Profiles & Shared Devices (Screen 1b)

A device can host **many Profiles** — the app is designed for shared devices (a family tablet, a kitchen device). A
Profile answers one question: *"who is using this app right now?"*

| Element       | Detail                                                                             |
|---------------|------------------------------------------------------------------------------------|
| Heading       | "Who's using Vesta?"                                                               |
| Profile cards | One card per Profile (display name + avatar). Tap to enter the app as that person. |
| "Add Profile" | Creates a new Profile → Profile Setup                                              |
| When shown    | If the device has ≥1 Profile and no active session                                 |

A Profile is the device-level answer to "who is using this app right now." It carries display name, avatar, and app
theme, and is how a shared device resolves the current user. Switching Profile switches the app's identity and theme. A
Home's members are the app users (Profiles) who joined it.

### Profile Setup (Screen 2)

A Profile is **required** before creating or joining a Home — you need a name to appear in a shared workspace. But setup
is lightweight — one required field, two optional.

| Field            | Required              | Detail                                                                                                                           |
|------------------|-----------------------|----------------------------------------------------------------------------------------------------------------------------------|
| **Display Name** | Yes                   | Free text. 2–40 characters. This is how others see you in a Home.                                                                |
| **Avatar**       | No                    | Choose from a curated set of presets (illustrated faces/objects). No photo upload in v1. Default assigned if skipped.            |
| **Theme**        | No (defaults applied) | Choose from: Default, High Contrast, Kid-Friendly. Can be changed later in Settings. Defaults to system preference if available. |

**Opinionated decision:** Display name is the only hard requirement. Avatar and theme are skippable — the user can
proceed immediately after entering a name. Do not gate on completeness. The goal is to get to a Home as fast as
possible.

**What happens if the user closes the app before finishing Profile setup?**
Nothing is saved. Next launch shows the Welcome screen again. A Profile is persisted only after the user submits a name.
This avoids orphaned partial profiles.

### Can the app be used before a Profile is set?

**Yes — only to create or pick a Profile.** The app has no useful household state without a Profile. There is no
"browse" mode. The Welcome → Profile flow is the mandatory entry point, but it is deliberately lightweight (one required
field) so the friction is minimal.

### AI Agent Path

An agent can set up a Profile programmatically:

- `create_profile(display_name: String, avatar_id?: String, theme?: ThemeEnum)`
- The agent supplies the human operator's name and preferences. No GUI interaction required.
- The agent inherits the human's theme preference if one is set on the device.

---

## 2. The "No Home Yet" State

After a Profile is set, the user lands on **Empty Home State** — a screen that makes the next step obvious.

### Screen: Empty Home State

| Element          | Detail                                                             |
|------------------|--------------------------------------------------------------------|
| Heading          | "Welcome, {name}"                                                  |
| Subtext          | "You're not in a Home yet. Create one or join one."                |
| Primary action   | **Create a Home** → Create Home flow                               |
| Secondary action | **Join a Home** → Join Home flow                                   |
| Theme            | Respects user's chosen theme                                       |
| Accessibility    | Two large, clearly separated touch targets. Readable at 200% zoom. |

**Opinionated decision:** These are the only two options. No "skip," no "explore," no "set up later." The user must be
in a Home to get value from Vesta. The screen is not a dashboard — it is a decision point.

### What is NOT shown

- No tool gallery (the Home's tools are on the Home Dashboard once the user is in a Home)
- No settings (nothing to configure yet)
- No "What's new" or onboarding tour (the flow IS the onboarding)

---

## 3. Creating a Home

### Flow: Create Home Form

A single form screen. Not a wizard. One screen, all fields visible.

| Field          | Required | Detail                                                                                                                   |
|----------------|----------|--------------------------------------------------------------------------------------------------------------------------|
| **Home Name**  | Yes      | Free text. 2–50 characters. Examples: "The Johnsons", "Flat 4B", "Mum's Place"                                           |
| **Home Shape** | Yes      | Choose from a fixed set (see table below). It describes the Home to its members; it does not change which tools are available.                                         |
| **Your Role**  | Auto-set | The creator is always **Admin**. No choice offered. Displayed as informational text: "You'll be the admin of this Home." |

### Home Shapes

| Shape            | Label            | Description                                                         |
|------------------|------------------|---------------------------------------------------------------------|
| `family`         | Family           | Parents, kids, or extended family living together                   |
| `flatshare`      | Flat Share       | Unrelated adults sharing a residence                                |
| `single_support` | Single + Support | One person with occasional help (carer, helper, family checking in) |
| `other`          | Other            | Doesn't fit the above — still creates a functional Home             |

**Opinionated decision:** Four options, not ten. The shape list is deliberately short. "Other" is always available so no
one is excluded. Shape can be changed later in Home Settings (out of scope for this spec, but the data model must
support it).

**Why shape is required at creation (not deferrable):**
Every Home has all of Vesta's tools; shape does not switch any of them on or off. It is the signal for how the Home is
described and presented (badge, wording, defaults), and it is cheapest to ask once, when the Home is created, rather than
leave Homes without one. The cost is one extra tap during creation.

### Success State

After submission:

1. Local Home record is created (immediate, offline).
2. The creator is added as the sole member with Admin role.
3. A unique **Home ID** is generated (UUID, stored locally).
4. A default **invite code** is generated (6-digit numeric, see §4).
5. User lands on the **Home Dashboard** (§5) — the empty shell, but now with the Home name and shape visible.

**Total time from "Get Started" to "Home created":** Target ≤ 60 seconds. Two screens: Profile (name) + Create Home
(name + shape).

### AI Agent Path

```
create_home(name: String, shape: HomeShapeEnum) → Home
```

The agent supplies name and shape. The human operator's Profile must already exist. Admin role is automatic.

---

## 4. Joining a Home

### How invites work (v1)

| Mechanism                 | Detail                                                                                                               |
|---------------------------|----------------------------------------------------------------------------------------------------------------------|
| **Invite Code**           | 6-digit numeric code. Generated per Home. Speakable, typeable, no ambiguity.                                         |
| **Multi-use (confirmed)** | One code per Home, shared by the whole family — any number of people can join with it. Revoked by rotating the code. |
| Code lifetime             | 7 days from creation. Expired codes are rejected silently (no error leak).                                           |
| Max pending codes         | 10 active codes per Home at a time. Oldest invalidated when limit hit.                                               |
| Code scope                | A code grants access to exactly one Home. No cross-Home tokens.                                                      |

**Opinionated decision:** Codes only, no deep links in v1. Deep links add platform complexity (intent handling,
universal links, clipboard parsing) for marginal benefit over a 6-digit code. A code can be communicated verbally, via
text, on a whiteboard — it works everywhere.

### Inviter Experience

The inviter (an Admin or Member of a Home) navigates to **Home Members** → **Invite Member**. The screen shows:

| Element             | Detail                                                                                             |
|---------------------|----------------------------------------------------------------------------------------------------|
| Current invite code | Displayed prominently (e.g., "482 917")                                                            |
| Regenerate button   | Generates a new code, invalidates the old one. Confirmation dialog required.                       |
| Code expiry         | "Expires in {days}d {hours}h"                                                                      |
| Instructions        | "Share this code with the person you want to invite. They'll enter it in Vesta to join your Home." |

**No contact-list integration.** No "send via SMS" or "send via email" in v1. The inviter copies or dictates the code
manually. This keeps the scope tight and avoids platform-specific share-sheet complexity.

### Joiner Experience (Screen: Join Home)

| Element       | Detail                                                                       |
|---------------|------------------------------------------------------------------------------|
| Heading       | "Join a Home"                                                                |
| Input field   | 6-digit code entry. Numeric keyboard on mobile. Auto-advance between digits. |
| Submit button | **Join** — disabled until 6 digits entered                                   |
| Back button   | Returns to Empty Home State                                                  |

**On valid code:**

1. The Home's name and shape are displayed for confirmation: "Join **Flat 4B** (Flat Share)?"
2. User taps **Confirm** to join.
3. User is added as a **Member** (not Admin).
4. User lands on the Home Dashboard (§5).

**On invalid/expired code:**

| Error                 | Message                                                                                           |
|-----------------------|---------------------------------------------------------------------------------------------------|
| Code not found        | "That code doesn't match a Home. Check the code and try again."                                   |
| Code expired          | "That code has expired. Ask the Home admin for a new one."                                        |
| Already a member      | "You're already in this Home." — Redirect to Home Dashboard.                                      |
| Joining your own Home | Not possible (you're already the Admin). Message: "You created this Home — you're already in it." |

**Opinionated decision:** Error messages are specific but do not leak information. "Code not found" does not say "no
Home with that ID exists" — it says the code doesn't match. This is a security posture, not paranoia.

### Edge Case: Multiple Homes

A user who is already in one Home can join another. The Empty Home State is never shown to a user who belongs to at
least one Home — they see the Home Dashboard with a Home switcher (§5). Joining a second Home follows the same code flow
but is initiated from the switcher or settings, not from the empty state.

### AI Agent Path

**Joining:**

```
join_home(invite_code: String) → Home
```

**Generating/rotating invite codes (admin only):**

```
generate_invite_code(home_id: UUID) → InviteCode
rotate_invite_code(home_id: UUID) → InviteCode
```

An agent acting on behalf of an admin can generate codes. The agent inherits the admin's permissions — a non-admin
member cannot generate codes.

---

## 5. "In a Home" State

### Home Dashboard

After creating or joining a Home, the user lands here. This is the persistent base screen of the app.

| Element               | Detail                                                                                                       |
|-----------------------|--------------------------------------------------------------------------------------------------------------|
| **Header**            | Home name + Home shape badge (e.g., "Family")                                                                |
| **Members**           | The Home's members — the app users who have joined. Visible immediately, populated from creation. See below. |
| **Tool tiles**        | A grid of tiles, one per tool (Pantry, Shopping List, Cookbook, Meal Planner, Calendar, Tasks…). Every tool is always present; nothing is added or chosen. |
| **Home switcher**     | Visible if user belongs to >1 Home. Dropdown or tab in the header.                                           |
| **Settings icon**     | Top-right. Leads to Home Settings (name, shape, members, invite, leave).                                     |

**Opinionated decision:** The dashboard is never a dead end. It shows the member list (always populated — social
proof the Home is real) and every tool as a tile, so the next action is always one tap away. There is no "Add Module"
step: users do not choose which tools they get, the app ships them all, and a tool the Home hasn't used yet simply opens
to its own empty state. This screen's job is to orient and direct.

### Member List (accessible from dashboard)

| Element         | Detail                                                                                                                                              |
|-----------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| List of members | Name + avatar + role badge (Admin/Member). Members are app users who joined the Home.                                                               |
| Invite          | "Invite someone" — surfaces the invite code (§4), one tap away. The most common first action after creating a Home is getting people in.            |
| Current user    | Highlighted or pinned to top                                                                                                                        |
| Leave Home      | Bottom of list. Confirmation required. Admin cannot leave if they are the sole admin (must transfer or disband first — out of scope for this spec). |

### Multiple Homes: The Home Switcher

| Behaviour       | Detail                                                                            |
|-----------------|-----------------------------------------------------------------------------------|
| When >1 Home    | A Home switcher appears in the app header/navigation                              |
| Switcher UI     | Dropdown or bottom sheet showing all Homes the user belongs to, with name + shape |
| Current Home    | Highlighted. Tap switches to that Home's dashboard.                               |
| Create new Home | Option in the switcher: "+ Create a Home" → re-runs Create Home flow              |
| Join new Home   | Option in the switcher: "+ Join a Home" → re-runs Join Home flow                  |
| Default Home    | The most recently active Home is shown on app launch.                             |

**Opinionated decision:** There is no "default Home" setting. The app simply remembers the last Home you were in. This
avoids a settings screen for something the app can infer.

---

## 6. State Model

Enumerated states for BDD scenario seeding. Each state is a distinct screen or modal the user can be in.

| State ID | State Name           | Description                                     | Entry Condition                            | Exit Actions                                                                                            |
|----------|----------------------|-------------------------------------------------|--------------------------------------------|---------------------------------------------------------------------------------------------------------|
| `S1`     | **Welcome**          | First-launch splash screen                      | App installed, no Profiles exist on device | Tap "Get Started" → `S2`                                                                                |
| `S1b`    | **ProfilePicker**    | "Who's using Vesta?" — pick or add Profile      | ≥1 Profile exists, no active session       | Pick Profile → `S3`/`S6` (by membership), "Add Profile" → `S2`                                          |
| `S2`     | **ProfileSetup**     | Display name + optional avatar + optional theme | After `S1` or from `S1b`                   | Submit valid name → `S3`                                                                                |
| `S3`     | **EmptyHome**        | "You're not in a Home yet" decision point       | Profile set, user belongs to 0 Homes       | Tap "Create" → `S4`, Tap "Join" → `S5`                                                                  |
| `S4`     | **CreateHomeForm**   | Form to create a new Home                       | From `S3` or Home Switcher                 | Submit valid form → `S6`                                                                                |
| `S5`     | **JoinHomeForm**     | Enter 6-digit invite code                       | From `S3` or Home Switcher                 | Valid code → confirmation → `S6`                                                                        |
| `S6`     | **HomeDashboard**    | Home shell with member list + tool tiles | User belongs to ≥1 Home                    | Open a tool, Members → `S7`, Settings → `S8`, Switch Home → `S6` (new Home context) |
| `S7`     | **MemberList**       | View members of current Home                    | From `S6`                                  | Back → `S6`, Leave Home → `S3` (if last Home) or `S6` (if >1 Home)                                      |
| `S8`     | **HomeSettings**     | Home name, shape, members, invite, roles        | From `S6`                                  | Back → `S6`                                                                                             |
| `S9`     | **InviteMember**     | Display/regenerate invite code                  | From `S8` or `S7`                          | Back → `S8`                                                                                             |
| `S10`    | **JoinConfirmation** | "Join **{HomeName}** ({shape})?"                | After valid code in `S5`                   | Confirm → `S6`, Cancel → `S5`                                                                           |
| `S11`    | **JoinError**        | Invalid/expired code message                    | After invalid code in `S5`                 | Dismiss → `S5`                                                                                          |

### BDD Scenario Seeds

Each state transition above maps to one or more scenarios:

```gherkin
# S1 → S2
Scenario: First launch shows welcome screen
  Given the app is freshly installed
  When the app launches
  Then the Welcome screen is displayed
  And the "Get Started" button is visible

# S2 → S3
Scenario: Profile setup with required name only
  Given the Welcome screen is displayed
  When the user taps "Get Started"
  Then the Profile Setup screen is displayed
  And the display name field is required
  And the avatar picker is optional
  And the app theme selector is optional

Scenario: Proceeding with name only
  Given the Profile Setup screen is displayed
  When the user enters a valid display name
  And skips avatar and theme
  And submits
  Then the Empty Home screen is displayed

# S1b → S2/S6
Scenario: Shared device shows profile picker
  Given the device already has one Profile
  And the current user's Profile has an active Home
  When the app launches
  Then the Profile Picker is displayed
  When the user picks their Profile
  Then the Home Dashboard for their Home is shown

# S3 → S4
Scenario: Creating a Home from empty state
  Given the user is on the Empty Home screen
  When the user taps "Create a Home"
  Then the Create Home form is displayed

# S4 → S6
Scenario: Successful Home creation
  Given the Create Home form is displayed
  When the user enters a Home name and selects a shape
  And submits
  Then a Home is created locally
  And the user is added as Admin
  And the user lands on the Home Dashboard

# S3 → S5
Scenario: Joining a Home from empty state
  Given the user is on the Empty Home screen
  When the user taps "Join a Home"
  Then the Join Home screen is displayed with a code input field

# S5 → S10 → S6
Scenario: Successful join with valid code
  Given the Join Home screen is displayed
  When the user enters a valid 6-digit code
  And taps "Join"
  Then the Join Confirmation screen shows the Home name and shape
  When the user confirms
  Then the user joins as a Member
  And the user lands on the Home Dashboard

# S5 → S11
Scenario: Join with expired code
  Given the Join Home screen is displayed
  When the user enters an expired code
  And taps "Join"
  Then an error message is shown: "That code has expired."
  And the user remains on the Join Home screen

# S5 → S11
Scenario: Join with invalid code
  Given the Join Home screen is displayed
  When the user enters a code that does not match any Home
  And taps "Join"
  Then an error message is shown: "That code doesn't match a Home."
  And the user remains on the Join Home screen

# S5 → S11
Scenario: Attempting to join own Home
  Given the user is the Admin of "My Home"
  When the user enters their own Home's invite code
  And taps "Join"
  Then an error message is shown: "You created this Home — you're already in it."
```

---

## 7. AI Agent Accessibility

**Governing rule:** An agent always inherits its human operator's permissions, never more. An agent can do anything its
operator can do — no more, no less.

### State-by-state agent access

| State               | GUI Action                    | Agent Equivalent                                                    | Permission Required  |
|---------------------|-------------------------------|---------------------------------------------------------------------|----------------------|
| `S1b` ProfilePicker | Pick Profile                  | `switch_profile(profile_id)`                                        | None                 |
| `S2` ProfileSetup   | Enter name, pick avatar/theme | `create_profile(name, avatar_id?, theme?)`                          | None (first-run)     |
| `S3` EmptyHome      | Tap "Create" or "Join"        | N/A (navigation — agent proceeds directly to action)                | Profile must exist   |
| `S4` CreateHomeForm | Fill and submit form          | `create_home(name, shape)`                                          | Profile exists       |
| `S5` JoinHomeForm   | Enter code and submit         | `join_home(invite_code)`                                            | Profile exists       |
| `S6` HomeDashboard  | View dashboard                | `get_home_dashboard(home_id)`                                       | Member of the Home   |
| `S7` MemberList     | View members                  | `list_members(home_id)`                                             | Member of the Home   |
| `S8` HomeSettings   | View/edit settings            | `get_home_settings(home_id)` / `update_home_settings(home_id, ...)` | Admin only for edits |
| `S9` InviteMember   | View/regenerate code          | `get_invite_code(home_id)` / `rotate_invite_code(home_id)`          | Admin only           |

### Agent-specific requirements

| ID   | Requirement                                                                                          | Priority |
|------|------------------------------------------------------------------------------------------------------|----------|
| AG-1 | Every entry-experience action has a corresponding function in the agent interface                    | Must     |
| AG-2 | Agent functions accept and return the same data as the GUI forms (no separate agent-only data model) | Must     |
| AG-3 | Agent errors mirror GUI error states (same codes, same messages)                                     | Must     |
| AG-4 | An agent creating a Home on behalf of a human uses the human's Profile permissions                   | Must     |
| AG-5 | An agent can be invoked before the GUI is ever opened — Profile setup via agent is valid             | Must     |
| AG-6 | Agent actions are logged in the same audit trail as GUI actions (no invisible writes)                | Must     |

### Example: Agent creating a Home for a grandmother

1. Grandson's agent calls `create_profile(display_name: "Margaret")` — sets up a Profile on the family tablet.
2. Grandson's agent calls `create_home(name: "Margaret's Place", shape: .single_support)` — creates the Home.
3. Grandson's agent calls `rotate_invite_code(home_id)` — generates a code to share with the family.
4. Grandma opens Vesta on her tablet. She sees the Home Dashboard. If this is her first time on the device, she taps her
   Profile; otherwise she's already in. She never touched a form.

This is a first-class use case, not a hack.

---

## 8. Non-Goals for This Slice

This spec deliberately does NOT cover:

| Excluded                                                  | Why                                                                                            |
|-----------------------------------------------------------|------------------------------------------------------------------------------------------------|
| Individual tools (Pantry, Shopping List, …)               | Each has its own spec. This spec ends at the dashboard that opens them.                        |
| Permission matrix (admin vs member capabilities in depth) | Covered at a high level here. Detailed RBAC is a separate concern.                             |
| Invite link / deep link handling                          | Codes only in v1. Links require platform-specific intent handling.                             |
| Invite security (rate limiting, abuse prevention)         | Security hardening is a separate workstream.                                                   |
| Home disbanding / deletion                                | Edge case for a later spec.                                                                    |
| Home shape editing after creation                         | Supported in the data model but not specified here.                                            |
| Profile editing after Profile setup                       | Out of scope. Name/avatar/theme can be changed — the settings flow is not specified here.      |
| Multi-device sync of Profiles                             | How identity propagates across devices is an architecture concern, not a product spec concern. |
| Notifications / alerts of any kind                        | Out of scope for this spec. Local task reminders were later added (ADR-032).                   |
| Onboarding tours, tooltips, or guided walkthroughs        | The flow IS the onboarding. No overlay tutorials.                                              |
| Error states not related to join codes                    | Network errors, storage errors, sync conflicts — architecture concerns.                        |

---

## 9. Decisions & Open Questions

### DQ-1: Invite codes — MULTI-USE (resolved)

**Decision:** One invite code per Home, used by the whole family. Multi-use. Any number of people can join with the same
code within its 7-day window. Access is revoked by rotating the code. *(Confirmed by CEO.)*

---

### DQ-2: Theme selection — via PROFILES, no age declaration (resolved)

**Decision:** No age-range self-declaration. Instead, the app supports **Profiles**: a device hosts multiple Profiles,
each with its own **display name, avatar, and app theme**. On a shared device the Profile Picker resolves the current
user. Themes accommodate the 8–100 age range and accessibility needs — kids get a Kid-Friendly theme, a Grandma with
poor vision gets High Contrast — chosen per Profile or by default from system accessibility settings. *(Confirmed by
CEO.)*

---

### DQ-3: Home cap — 10 (resolved)

**Decision:** A user can belong to **at most 10 Homes**. Message on hitting the cap: "You've reached the maximum number
of Homes. Leave a Home to create or join a new one." *(Confirmed by CEO.)*

---

### DQ-4: Dashboard content — MEMBERS (resolved)

**Decision:** The dashboard shows the **member list** immediately (the app users who have joined the Home), with the
invite code one tap away. A Home's community is visible from creation, and the natural next action is inviting people
in. *(Per CEO — the earlier "People" concept and x0x agent-id linking are parked for now.)*