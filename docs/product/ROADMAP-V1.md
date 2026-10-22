# Vesta — Roadmap V1

> **Updated 2026-10-16.** The original plan (email accounts, one tool at a time, 18 weeks) did not survive contact with
> the build. This version records what Vesta is now and what is left. Per-tool detail lives in the ADRs in
> [docs/adr](../adr); platform status is in the [README](../../README.md).

## Philosophy

Ship the smallest thing that proves the core hypothesis: **households will adopt a tool if it delivers immediate,
obvious value.** Vesta is one app. Its tools (Pantry, Shopping List, Cookbook, Meal Planner, Tasks and Chores, Calendar,
Marketplace) ship together; users do not pick which to install.

---

## The Core Hypothesis

> If we give a household a tool that solves a painful coordination problem — and make it dead simple —
> they'll adopt it and ask for more.

Tools must:

1. Deliver value on day one (no setup-heavy prerequisites)
2. Involve multiple household members (prove the coordination model)
3. Be universally applicable across household types (families, flat shares, single + support)

---

## Where Things Stand

### Foundation — built

- **No accounts.** A Home is created on a device; there is no sign-up and no server.
- Homes, member roles (admin / member), invite links as the one way to join (ADR-014, ADR-018, ADR-019), profiles.
- Peer-to-peer sync over x0x, between members and between a person's own devices, with optional always-on hubs
  (ADR-020) so devices need not be online together.
- Local-first storage: Automerge documents persisted in SQLite (ADR-005, ADR-007).
- Assistants: scoped, per-Home permissions for external AI agents (ADR-009, ADR-027).

### Tools — built

| Tool | Notes | ADR |
|---|---|---|
| Pantry and Shopping List | Stock counts, shared list, "add checked to Pantry" | 008 |
| Cookbook | Recipes shared by the Home | 022 |
| Meal Planner | Week view with breakfast, lunch and dinner slots | 028 |
| Calendar | Shared events plus a private per-Home calendar for your own devices | 025, 029 |
| Tasks and Chores | Shared tasks, recurrence, completion history; local reminders | 031, 032 |
| Marketplace | Browse local businesses (Overture places); My Shops favourites that Pantry and Shopping link to | 026, 039 |

Also built: welcome tour (ADR-030) and a sync activity view (ADR-033).

### Still to do

- Task **rotation** (round-robin and so on), deferred by ADR-031.
- Linking the tools: the Meal Planner feeding the shopping list and Pantry, tasks referencing list items.
- Real-world use: much of the UI has been tested but not yet run for real by households. Each ADR's As-built notes
  list its known gaps.
- Platforms: iOS is not supported; Windows is not working out of the box; macOS is untested (see the README).
- A monetisation strategy. None is decided (the earlier freemium / premium-module plan was dropped).

---

## What's NOT in Scope

- Financial / expense management (permanent, see [NON-GOALS.md](NON-GOALS.md))
- Smart home / IoT, social features, AI "life coach" nudging
- Push notifications that need a server (local reminders on each device are in; see ADR-032)
- Competing with dedicated recipe apps or full calendar products (see NON-GOALS.md)

---

## Technical Direction (Confirmed)

- **Team size:** 1 developer.
- **Platforms:** Android and desktop (Linux) from one **Kotlin Multiplatform** / Compose Multiplatform codebase.
- **Accounts:** none. Local-first, no sign-up.
- **Transport:** Decentralised P2P — **x0x**. No central servers.
- **Testing:** **TDD / BDD from day one** — mandatory, not optional.
- **Offline:** Offline/local-first architecture from day one.

---

## Success Metrics for V1

| Metric | Target | Why |
|---|---|---|
| Activation rate | >40% of installs create or join a Home and use a tool within 24h | Proves onboarding works |
| Week-1 retention | >50% of Homes active after 7 days | Proves immediate value |
| Month-1 retention | >30% of Homes active after 30 days | Proves sustained value |
| Task completion rate | >60% of created tasks marked complete | Proves the tool drives action, not just lists |
| Tool adoption | >50% of active Homes use more than one tool | Proves the tools work together |

Vesta has no accounts, server or analytics, so these need an opt-in measure or direct feedback from households.

---

## What Comes After V1 (Directions — Not Committed)

1. **Chore gamification** — points, rewards, friendly competition (if data supports it)
2. **Third-party API** — let others build on Vesta (assistants over x0x are the first step)
3. **Household messaging** — structured notes/communication

---

## Open Questions

- [ ] **How to measure the success metrics** without accounts or servers.
- [ ] **Monetisation:** undecided.
- [ ] **Mobile data and battery cost:** x0x on Android uses noticeable data and battery; not yet measured or optimised.
