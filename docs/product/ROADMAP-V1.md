# Vesta — Roadmap V1

## Philosophy

Ship the smallest thing that proves the core hypothesis: **households will adopt a modular tool if the first module
delivers immediate, obvious value.**

One module. Done well. Then iterate.

---

## The Core Hypothesis

> If we give a household a single tool that solves their most painful coordination problem — and make it dead simple —
> they'll adopt it and ask for more.

The first module must:

1. Deliver value on day one (no setup-heavy prerequisites)
2. Involve multiple household members (prove the coordination model)
3. Be universally applicable across household types (families, flat shares, single + support)

**Candidate:** Task/Chore Management with Rotation.

**Why this first?**

- Every household type needs it
- It immediately surfaces the coordination problem Vesta solves
- It's simple enough to build quickly, complex enough to demonstrate modularity
- It creates a natural hook for the next module (shared lists for supplies)

---

## Phased Roadmap

### Phase 0 — Foundation (Weeks 1-4)

*Goal: A working household system with authentication.*

- User account creation (email auth)
- Household creation and invitation flow
- Member roles (admin / member)
- Basic UI shell (navigation, household switcher)
- **Exit criteria:** Two users can create a household, invite each other, and see each other as members.

### Phase 1 — First Module: Tasks (Weeks 5-10)

*Goal: A household can coordinate chores using Vesta.*

- Create, assign, complete tasks
- Recurring task creation (daily, weekly, custom)
- Rotation assignment (round-robin, random, or sequential)
- Household dashboard (outstanding, overdue, completed)
- Task history
- Basic notifications (assignment, due date)
- **Exit criteria:** A real household uses Vesta for chore management for 1+ weeks and can articulate why it's better
  than their previous method.

### Phase 2 — Second Module: Shared Lists (Weeks 11-14)

*Goal: The household can manage shared supplies.*

- Shared list creation (groceries, supplies, etc.)
- Add / check off items
- List visibility for all household members
- Integration point: tasks can reference lists (e.g., "Buy dish soap" links to grocery list)
- **Exit criteria:** Lists and tasks are used together; users see them as one coherent system, not two separate
  features.

### Phase 3 — Polish & Retention (Weeks 15-18)

*Goal: Users stay after the novelty wears off.*

- Improved notifications (digest, configurable)
- Activity feed (what happened in the household today)
- Profile customisation (avatar, preferences)
- UX polish pass based on Phase 1/2 user feedback
- Performance optimisation
- **Exit criteria:** 30-day retention meets or exceeds target (>30%).

---

## What's NOT in V1

- Financial / expense management (out of scope entirely)
- Meal planning (out of scope entirely)
- Calendar integration
- Notifications
- Third-party integrations
- Gamification / points / rewards (v2 candidate)
- AI features

---

## Technical Direction (Confirmed)

- **Team size:** 1 developer.
- **Platforms:** Cross-platform desktop + mobile (iOS & Android), from one codebase using **Kotlin Multiplatform**.
- **Auth:** An implementation detail, out of scope for product discussion.
- **Transport:** Decentralised P2P — **x0x** and **Autonomi**. No central servers.
- **Testing:** **TDD / BDD from day one** — mandatory, not optional.
- **Offline:** Offline/local-first architecture from day one.

---

## Success Metrics for V1

| Metric               | Target                                                  | Why                                           |
|----------------------|---------------------------------------------------------|-----------------------------------------------|
| Activation rate      | >40% of sign-ups create a household + first task in 24h | Proves onboarding works                       |
| Week-1 retention     | >50% of households active after 7 days                  | Proves immediate value                        |
| Month-1 retention    | >30% of households active after 30 days                 | Proves sustained value                        |
| Task completion rate | >60% of created tasks marked complete                   | Proves the tool drives action, not just lists |
| Module adoption      | >50% of active households add the Lists module in v1    | Proves modularity model works                 |

---

## What Comes After V1 (V2 Directions — Not Committed)

Based on user feedback and retention data, potential v2 modules:

1. **Chore gamification** — points, rewards, friendly competition (if data supports it)
2. **Calendar integration** — basic household event coordination
3. **Third-party module API** — let others build modules for Vesta
4. **Household messaging** — structured notes/communication as a first-class module

> **Note:** Financial / expense sharing and meal planning are permanently out of scope (see NON-GOALS.md). They are not
> V2 candidates.

---

## Open Questions

- [ ] **Timeline realism for a solo dev:** The 4-phase/18-week estimate assumed a small team. With one developer, this
  is optimistic. Revisit phase sizing once the team/context is set.
- [ ] **x0x / Autonomi maturity:** Roadmap pacing depends on how much scaffolding the P2P transport layer requires vs.
  what these technologies provide out of the box. Needs a spike at Phase 0 start.
