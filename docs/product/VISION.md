# Vesta — Product Vision

## Vision Statement

> Every household deserves tools as adaptable as the people who live in them. Vesta exists to replace the fragmented,
> one-size-fits-all approach to home management with a set of connected tools that shapes itself to any household — from a single
> person to a large family.

## What Does Success Look Like?

**In 12 months:**

- A household can install Vesta, create a Home, and be actively using the tools they need within 5 minutes.
- The core tools (Pantry, Shopping List and the rest) are stable enough that users prefer Vesta over their current spreadsheet / app / mental model within 2
  weeks of use.
- At least one tool has clear product-market fit evidenced by retention data.

**In 3 years:**

- Vesta is the default starting point when someone thinks "I need to get my household organised."
- A growing set of tools that work together, and an open way for AI agents and others to extend what Vesta can do.
- Household management is no longer something people tolerate bad tools for.

## High-Level Goals

| # | Goal                                     | Why It Matters                                                                                                      |
|---|------------------------------------------|---------------------------------------------------------------------------------------------------------------------|
| 1 | **Solve the "household shapes" problem** | Current tools assume a nuclear family. Most households aren't that.                                                 |
| 2 | **Connected, not fragmented**            | Every tool shares one Home, so there are no disconnected silos of data.                                             |
| 3 | **Low barrier, high ceiling**            | Pick up and use in minutes, but powerful enough for power users.                                                    |
| 4 | **Local-first, no account harvesting**   | Use Vesta without an account or surrendering your data. Privacy is foundational, not a feature.                     |
| 5 | **Sustainable funding**                  | The product needs a way to fund itself. The monetisation strategy has not been decided.                             |
| 6 | **Accessible to everyone (8-100)**       | Simple and clear enough for a grandma with poor visibility, engaging enough for a child.                            |
| 7 | **Every capability open to AI agents**   | Anything a human can do in the GUI, an AI agent can do via Skills / MCP servers. One functionality, two interfaces. |

## Guiding Principles

1. **Every household is different.** If a feature only works for one household type, it needs to adapt (or stay out of the way), not be hard-coded for one.
2. **Ship tools, not platforms.** Each tool (Pantry, Shopping List, Cookbook…) should deliver standalone value. Integration is a bonus.
3. **Earn the next click.** Every screen should make the user's life simpler, not busier.
4. **Privacy is non-negotiable.** Household data is intimate. Treat it that way. No account, no harvesting, no central
   servers.
5. **Accessibility is a first-class feature.** If a grandma with poor vision can't use it comfortably, we haven't
   finished it.
6. **The GUI is not the product's only face.** Every user action has an equivalent agent action, exposed via Skills /
   MCP servers. An AI agent must be able to do anything the GUI allows — always acting with its human operator's
   permissions and never more.

---

## Platform & Architecture Direction (High Level)

- **Platforms:** Cross-platform desktop + mobile (iOS & Android), built from day one from a single Kotlin Multiplatform
  codebase.
- **No accounts:** The app is local-first. No sign-up required to use it.
- **Transport:** Decentralised, peer-to-peer technologies (x0x and Autonomi). No central servers, no data residency
  concerns.
- **Tools:** Vesta is split into tools such as Pantry and Shopping List, but users cannot pick which they get; the app
  ships them all.
- **Revenue:** Not yet decided. The earlier freemium / premium-module plan has been dropped.
- **Testing:** TDD / BDD from day one.

*(Implementation specifics intentionally deferred — this section captures decisions only.)*

---

## Multi-User & "Homes" Model

- Users can create multiple **Homes** (capped at 10).
- **Profiles** are device-level: they answer "who is using this device right now" (shared tablets, family devices),
  carrying a display name, avatar, and app theme.
- A user (acting through a Profile) joins a Home via an invite code; within a Home they share and modify a **shared
  workspace** alongside the other members who joined.

---
