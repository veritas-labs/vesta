# Vesta — Requirements

High-level requirements at the "what" level. Not "how." These are the foundation for user stories and acceptance
criteria later.

---

## Functional Requirements

### Household Setup & Management

| ID   | Requirement                                                                                                            | Priority |
|------|------------------------------------------------------------------------------------------------------------------------|----------|
| FR-1 | A user can create a "home" and define its members                                                                      | Must     |
| FR-2 | A home can have different shapes/types (family, flat share, single, etc.) and this affects which modules are suggested | Must     |
| FR-3 | Members can be invited to join a home via link or code                                                                 | Must     |
| FR-4 | A home admin can assign roles (admin, member) with appropriate permissions                                             | Must     |
| FR-5 | Members can leave a home, and their data is handled cleanly                                                            | Should   |

### Notes on "Home" Terminology

CEO has defined a clear multi-user model: users create multiple **homes**, each user has their own identity, and joins a
home to work in a **shared workspace**. The term "home" replaces "household" in the user-facing model.

### Core Modules (V1)

#### Task/Chore Management

| ID    | Requirement                                                                          | Priority |
|-------|--------------------------------------------------------------------------------------|----------|
| FR-10 | Users can create tasks with title, description, due date, and assignee               | Must     |
| FR-11 | Tasks can be assigned to individual members or set on a rotation                     | Must     |
| FR-12 | Tasks can be marked complete by the assignee or an admin                             | Must     |
| FR-13 | Recurring tasks can be created with configurable frequency (daily, weekly, custom)   | Must     |
| FR-14 | A household dashboard shows outstanding tasks, overdue items, and recent completions | Must     |
| FR-15 | Task history is retained and visible (who did what, when)                            | Should   |
| FR-16 | A task with a due time can remind its assignees with a local notification (ADR-032)  | Should   |

> **Amended 2026-10-14 (ADR-031):** rotation (FR-11's second half) is deferred past the first Tasks release, and FR-12 is
> widened: any Home member may mark any task complete, with who did it recorded.

#### Shared Lists

| ID    | Requirement                                                            | Priority |
|-------|------------------------------------------------------------------------|----------|
| FR-20 | Users can create shared lists (groceries, supplies, etc.)              | Must     |
| FR-21 | List items can be added by any member and checked off by any member    | Must     |
| FR-22 | Lists persist across sessions and are visible to all household members | Must     |
| FR-23 | Items can be categorised or grouped (e.g., by store, by room)          | Should   |

#### Basic Communication

| ID    | Requirement                                            | Priority |
|-------|--------------------------------------------------------|----------|
| FR-30 | Users can post messages/notes visible to the household | Should   |
| FR-31 | Users can leave comments on tasks or lists             | Should   |

> **Note:** Push notifications are out of scope. **Local task reminders are in scope** (amended 2026-10-14, ADR-032): each
> device schedules its own, with no server. See NFR section.

### User Identity

| ID    | Requirement                                                                                             | Priority |
|-------|---------------------------------------------------------------------------------------------------------|----------|
| FR-40 | Users can establish a local identity without being forced to create an online account or surrender data | Must     |
| FR-41 | A user can belong to multiple homes (capped at 10)                                                      | Must     |
| FR-42 | Users can manage their profile (name, avatar, theme preference)                                         | Should   |

### Profiles (device-level)

A Profile identifies the **current app user on a device** — essential for shared devices (a family tablet).

| ID    | Requirement                                                                        | Priority |
|-------|------------------------------------------------------------------------------------|----------|
| FR-43 | A device can host multiple Profiles                                                | Must     |
| FR-44 | Profile creation captures display name, avatar, and app theme                      | Must     |
| FR-45 | The app shows a Profile Picker when no active session exists and ≥1 Profile exists | Must     |
| FR-46 | Switching Profile switches the current app user, including their theme             | Must     |
| FR-47 | Profile is the mandatory prerequisite before creating or joining a Home            | Must     |

### AI Agent Accessibility

**Principle:** Human users and AI agents are equal citizens of Vesta. Anything a human can do via the GUI, an AI agent
can do via Skills / MCP servers.

| ID    | Requirement                                                                                                                                                                    | Priority |
|-------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------|
| FR-60 | Feature parity: every action performable in the GUI has an equivalent programmatic interface accessible to AI agents                                                           | Must     |
| FR-61 | Agents can discover available capabilities and how to use them (self-describing interface)                                                                                     | Must     |
| FR-62 | Agents can perform all core operations: manage homes and members, create/assign/complete tasks, manage shared lists                                                            | Must     |
| FR-63 | Agent actions are subject to the exact same role/permission rules as GUI actions — an agent **always inherits its human operator's permissions, and never gets anything more** | Must     |
| FR-64 | Agents can query household state (reads) in addition to performing actions (writes)                                                                                            | Must     |
| FR-65 | Agent access works in the same local-first/P2P model — no central server dependency                                                                                            | Must     |

> **Note:** Exposed via Skills / MCP servers (per CEO). This is a product requirement, not an afterthought — agent
> access is designed in from day one.

---

## Non-Functional Requirements

### Performance

| ID    | Requirement                                                                                                                                                |
|-------|------------------------------------------------------------------------------------------------------------------------------------------------------------|
| NFR-1 | Core screens (dashboard, task list) load quickly — <2 seconds on a standard connection, effectively instant on local data                                  |
| NFR-2 | App remains responsive with up to 50 members in a home                                                                                                     |
| NFR-3 | Offline-first: the app is fully usable offline as the primary mode (local-first). Synchronisation is handled by the P2P transport when peers are reachable; an optional always-on hub (a plain x0x daemon) lets members sync without being online together ([docs/product/SYNC-AND-JOINING.md](SYNC-AND-JOINING.md)) |

### Security & Privacy

| ID     | Requirement                                                  |
|--------|--------------------------------------------------------------|
| NFR-10 | All data encrypted in transit (TLS 1.2+) and at rest         |
| NFR-11 | Home data is isolated — no cross-home data leakage, ever     |
| NFR-12 | Users can export their data                                  |
| NFR-13 | Users can delete their account and all associated data       |
| NFR-14 | No advertising. No data selling. Full stop.                  |
| NFR-15 | No mandatory account or sign-up to use the app (local-first) |

> **Note:** Because Vesta is local-first with no central servers, there are no data-residency concerns. This is a
> non-issue by architecture (confirmed by CEO).

### Accessibility & Theming

| ID     | Requirement                                                                                                |
|--------|------------------------------------------------------------------------------------------------------------|
| NFR-20 | Core flows usable with keyboard navigation                                                                 |
| NFR-21 | Minimum WCAG 2.1 AA compliance                                                                             |
| NFR-22 | Readable font sizes, high contrast mode available                                                          |
| NFR-23 | App supports themes that adapt to user needs — including a kid-friendly mode — since age range spans 8–100 |
| NFR-24 | Simple and clear enough for users with poor visibility to use comfortably (a stated product requirement)   |

### Scalability

| ID     | Requirement                                                                 |
|--------|-----------------------------------------------------------------------------|
| NFR-30 | System supports 10,000 households at launch without performance degradation |
| NFR-31 | Architecture allows adding new modules without core system changes          |

### Usability

| ID     | Requirement                                                                           |
|--------|---------------------------------------------------------------------------------------|
| NFR-40 | New user can create a Home and land on its Dashboard within 5 minutes of first launch |
| NFR-41 | No screen in the app requires more than 3 taps/clicks from the dashboard              |

### AI Agent Integration

| ID     | Requirement                                                                                                                                 |
|--------|---------------------------------------------------------------------------------------------------------------------------------------------|
| NFR-50 | The MCP server / agent interface is tested under TDD/BDD with the same rigour as GUI features                                               |
| NFR-51 | Agent interface latency is not a barrier: local actions resolve promptly (local-first, no round-trips to a server)                          |
| NFR-52 | Agent actions are auditable in household state/history like any user action — no invisible writes                                           |
| NFR-53 | The agent interface exposes the same theming/accessibility context where relevant (e.g., an agent respecting a member's kid-friendly theme) |

---

## Resolved Decisions

- **Offline mode:** Must-have from day one. Offline/local-first is the core architecture.
- **Multi-home:** Supported from day one. User identities join multiple homes. Not considered complex given the P2P
  architecture.
- **Data residency:** Non-issue — no central servers by design.
- **Notifications:** No push or server-sent notifications. Local task reminders are in scope (ADR-032), planned and shown by
  each device from its own copy of the data. *(Amended 2026-10-14; previously "out of scope for v1 entirely".)*
- **Accounts:** No mandatory sign-up / account to use the app.
- **AI agent access:** Required from day one. Feature parity between GUI and agent interfaces (Skills / MCP servers). An
  agent always acts with the permissions of its human operator — never more.
- **Invite codes:** Multi-use — one code per Home, shared by the whole family; revoked by rotation.
- **Home cap:** A user can belong to at most 10 Homes.
- **Profiles:** Device-level concept — the current app user. A device hosts many Profiles (shared tablets), each with
  display name, avatar, and app theme. Switching Profile switches the app user and theme. A Home's members are the app
  users who joined it. *(The earlier "People" concept and x0x agent-id linking are parked for now.)*

## Open Questions

- [ ] **Only-visible-when-online comms:** Local reminders (ADR-032) cover due times, but how do task assignments/recurrences
  surface to members in an offline-first model (e.g., an in-app "inbox" or activity feed)? Needs UX thinking.
