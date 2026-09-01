# Vesta — Product Vision

## Vision Statement

> Every household deserves tools as adaptable as the people who live in them. Vesta exists to replace the fragmented, one-size-fits-all approach to home management with a modular system that shapes itself to any household — from a single person to a large family.

## What Does Success Look Like?

**In 12 months:**
- A household can sign up, pick the modules they need, and be actively using Vesta within 5 minutes.
- Core modules are stable enough that users prefer Vesta over their current spreadsheet / app / mental model within 2 weeks of use.
- At least one module has clear product-market fit evidenced by retention data.

**In 3 years:**
- Vesta is the default starting point when someone thinks "I need to get my household organised."
- A healthy ecosystem of modules — some built by us, some by the community.
- Household management is no longer something people tolerate bad tools for.

## High-Level Goals

| # | Goal | Why It Matters |
|---|------|---------------|
| 1 | **Solve the "household shapes" problem** | Current tools assume a nuclear family. Most households aren't that. |
| 2 | **Modularity without fragmentation** | People should add tools without creating disconnected silos of data. |
| 3 | **Low barrier, high ceiling** | Pick up and use in minutes, but powerful enough for power users. |
| 4 | **Local-first, no account harvesting** | Use Vesta without an account or surrendering your data. Privacy is foundational, not a feature. |
| 5 | **Freemium modules** | Core value is free. Premium modules fund the product. |
| 6 | **Accessible to everyone (8-100)** | Simple and clear enough for a grandma with poor visibility, engaging enough for a child. |
| 7 | **Every capability open to AI agents** | Anything a human can do in the GUI, an AI agent can do via Skills / MCP servers. One functionality, two interfaces. |

## Guiding Principles

1. **Every household is different.** If a feature only works for one household type, it's a module, not core.
2. **Ship tools, not platforms.** Modules should deliver standalone value. Integration is a bonus.
3. **Earn the next click.** Every screen should make the user's life simpler, not busier.
4. **Privacy is non-negotiable.** Household data is intimate. Treat it that way. No account, no harvesting, no central servers.
5. **Accessibility is a first-class feature.** If a grandma with poor vision can't use it comfortably, we haven't finished it.
6. **The GUI is not the product's only face.** Every user action has an equivalent agent action, exposed via Skills / MCP servers. An AI agent must be able to do anything the GUI allows — always acting with its human operator's permissions and never more.

---

## Platform & Architecture Direction (High Level)

- **Platforms:** Cross-platform desktop + mobile (iOS & Android), built from day one from a single Kotlin Multiplatform codebase.
- **No accounts:** The app is local-first. No sign-up required to use it.
- **Transport:** Decentralised, peer-to-peer technologies (x0x and Autonomi). No central servers, no data residency concerns.
- **Revenue:** Freemium modules. Core is free; premium modules are paid.
- **Testing:** TDD / BDD from day one.

*(Implementation specifics intentionally deferred — this section captures decisions only.)*

---

## Multi-User & "Homes" Model

- Users can create multiple **Homes** (capped at 10).
- **Profiles** are device-level: they answer "who is using this device right now" (shared tablets, family devices), carrying a display name, avatar, and app theme.
- A user (acting through a Profile) joins a Home via an invite code; within a Home they share and modify a **shared workspace** alongside the other members who joined.

---
