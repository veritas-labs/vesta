# Vesta — Non-Goals

This file is intentionally opinionated. Knowing what we're NOT building is as important as knowing what we are.

## Explicitly Out of Scope (V1+)

### 1. Smart Home / IoT Integration

Vesta is not a home automation platform. We don't control lights, thermostats, or locks. There are plenty of products
doing that. We manage the *human* side of running a home.

### 2. Financial Management / Banking (all of it)

All financial management is out of scope — for v1 **and** beyond. We're not tracking bank accounts, handling payments
between household members, managing budgets, or splitting expenses. This is a hard line, not a "maybe later." If users
want expense splitting, they use a dedicated tool.

### 3. Social Network / Community Features

Vesta is for the household, not the neighbourhood. No neighbourhood feeds, no community boards, no social features
beyond what's needed for household collaboration.

### 4. AI-Powered "Life Coach"

We're not going to lecture users about what they should be doing. No AI suggestions like "you haven't cleaned the
bathroom in 12 days." Tools should inform, not nag. The user is in control.

### 5. Full Calendar / Scheduling Platform

We won't replace Google Calendar or Outlook. If a tool needs basic date/time coordination (e.g., chore schedules),
fine. A full family calendar with external integrations is a separate product.

### 6. Meal Planning / Recipe Management

Meal planning and recipe management are out of scope — for v1 and beyond. It's a deep, competitive domain (Mealime,
Paprika, etc.) and doesn't align with our core coordination focus. Confirmed: not a differentiator for Vesta.

### 7. Pet Management, Plant Care, Vehicle Maintenance...

The temptation will be to keep adding niche tools. For v1, we draw the line. Every new tool has to justify itself
against our core use cases.

---

## Decision Rule

If a proposed feature doesn't directly help **a household coordinate, communicate, or manage recurring tasks**, it
doesn't belong in Vesta's core. It might become its own tool eventually, but the bar is high.

---

## Additional Non-Goals (Confirmed by CEO)

- **No mandatory account / sign-up to use the app.** The app is local-first; using it must not require creating an
  account or surrendering data.
- **No push notifications or notification servers.** Vesta has no server to send them. *(Amended 2026-10-14: the earlier
  blanket "no notifications (v1)" was reversed for **local** task reminders, which each device schedules for itself with no
  account and no network; see ADR-032. Alerts that need a server remain out.)*
