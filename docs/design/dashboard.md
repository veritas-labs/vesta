# Home Dashboard, Member List & Home Switcher

> Visual/interaction design layer for the persistent base screen of the app. Behavioral contract: CORE-EXPERIENCE.md §5
> "'In a Home' State" and §6 State Model (`S6`, `S7`).

## 1. Purpose & States Covered

| State ID           | Screen         | CORE-EXPERIENCE ref                                                                                |
|--------------------|----------------|----------------------------------------------------------------------------------------------------|
| `S6`               | Home Dashboard | §5 "Home Dashboard (Empty — No Modules)"                                                           |
| `S7`               | Member List    | §5 "Member List (accessible from dashboard)"                                                       |
| *(no separate ID)* | Home Switcher  | §5 "Multiple Homes: The Home Switcher" — folded into `S6`'s exit/entry actions per the state table |

This is the screen a user spends the most time on, across every future module. Everything here must be designed to
**extend gracefully** once modules exist, even though module content itself is out of scope for this spec.

## 2. Target Platform & Responsive Behavior

- **Mobile (~360–420dp):** single-column, vertically stacked: Header → Member row (horizontal scroll) → Module area.
- **Tablet (~600–1024dp) and Desktop (≥1024dp): two-column layout.** This is the most significant tablet/desktop
  divergence in the whole entry-experience design set, called out per the brief:
    - **Left column (~30–35% width, persistent panel):** Member List, rendered inline rather than requiring navigation
      to a separate screen. `S7` becomes an *inline panel* on wide viewports instead of a full-screen push.
    - **Right column (~65–70% width):** Module area — today, the empty state; in future, a grid of module cards.
    - Header spans the full width above both columns.
- Breakpoint values are not specified anywhere in the source docs; assumed standard three-tier breakpoints (compact <
  600dp / medium 600–839dp / expanded ≥840dp), consistent with common Compose Multiplatform `WindowSizeClass`
  conventions — flagged in Open Questions.

## 3. Layout — Mobile Reference

```
┌─────────────────────────────┐
│ The Johnsons  [Family ▾] [⚙]│  ← header: name, shape badge, switcher chevron, settings
├─────────────────────────────┤
│ ( ● )( ● )( ● )( + Invite ) │  ← member avatar row, horizontal scroll
├─────────────────────────────┤
│                             │
│     ( illustration )        │
│  No modules yet. Add your   │
│  first tool to get started. │
│                             │
│                     [ + ]   │  ← Add Module FAB, bottom-right, persistent
└─────────────────────────────┘
```

## 4. Layout — Tablet/Desktop Reference

```
┌───────────────────────────────────────────────────┐
│ The Johnsons  [Family ▾]                      [⚙]  │
├───────────────────┬───────────────────────────────┤
│ Members            │                               │
│ ● Sarah (You)       │      ( illustration )         │
│ ● Tom   Admin       │   No modules yet. Add your     │
│ ● Priya             │   first tool to get started.   │
│ ● Jake              │                                │
│ [ + Invite someone ]│                       [ + ]    │
└───────────────────┴───────────────────────────────┘
```

## 5. Component Inventory

| Component                                              | Anatomy                                                                                                                                                                            |
|--------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **App Header Bar**                                     | Home name (bold, truncates with ellipsis if long), Shape Badge beside it, chevron icon (only if user belongs to >1 Home — opens Home Switcher), Settings icon button top-right.    |
| **Shape Badge**                                        | Small pill: icon + label (e.g., house-outline icon + "Family").                                                                                                                    |
| **Member Avatar Row** (mobile)                         | Horizontal-scroll strip of Member Avatar Chips, current user pinned first, trailing dashed "+ Invite" tile.                                                                        |
| **Member Avatar Chip**                                 | Circular avatar (~40–48dp), small role indicator (star/crown for Admin) as an overlay badge, name shown on tap/long-press (mobile) or always-visible label (tablet+ inline panel). |
| **Members Panel** (tablet/desktop)                     | Vertical list version of the above: avatar + name + role badge text ("Admin"/"Member") + "You" tag for current user, "+ Invite someone" row pinned at the bottom of the panel.     |
| **Empty Module Illustration**                          | Single friendly graphic signaling "nothing here yet, but that's fine."                                                                                                             |
| **Add Module Button**                                  | Bottom-right FAB on mobile (circular, "+" icon); becomes a labeled pill button ("+ Add Module") in the same bottom-right corner of the module column on tablet/desktop.            |
| **Home Switcher Sheet** (mobile)                       | Bottom sheet: list of Home Rows, divider, "+ Create a Home" / "+ Join a Home" action rows.                                                                                         |
| **Home Switcher Dropdown** (tablet/desktop)            | Anchored popover under the header Home name instead of a full-screen sheet — appropriate given more screen real estate and pointer precision.                                      |
| **Home Row** (in switcher)                             | Home name + shape badge + role indicator + checkmark if current.                                                                                                                   |
| **Full Member Row** (`S7`, mobile full-screen version) | Larger avatar, name, role badge pill, "You" tag, tap target for member detail (out of scope beyond this).                                                                          |
| **Leave Home Row**                                     | Bottom of Member List, styled with a warning (not alarming) tone; opens a Confirmation Dialog.                                                                                     |
| **Confirmation Dialog (Leave Home)**                   | Modal: warning icon, explanatory text, Cancel + destructive-styled Leave buttons.                                                                                                  |

## 6. Visual Hierarchy & Emphasis

> **Design decision (ties to CORE-EXPERIENCE §5 opinionated decision — "not a dead end"):** the **Add Module button** is
> the single most prominent actionable element on first arrival — largest touch target, strongest fill color, and (on
> mobile) the only floating/elevated element on the screen. The Member row, while visible immediately as "social proof the
> Home is real," is intentionally lower visual weight: smaller avatars, muted labels — present, but ambient, not competing
> with the one clear next action.

The "+ Invite" trailing tile is the one member-list element that shares some of the Add Module button's visual energy,
because inviting people is the natural, most common first action after creating a Home (per CORE-EXPERIENCE §5: "The
most common first action after creating a Home is getting people in").

> **Design decision:** Add Module is specified as a **persistent FAB (mobile) / persistent bottom-right button (
tablet+)** rather than an inline button embedded in the empty-state copy block. This is a forward-looking choice: once
> modules exist and populate the right column, the FAB stays anchored in the same physical position, so "add a module"
> never has to be re-discovered as the screen's content grows. This extends beyond the current empty-only spec slice but
> avoids a redesign the moment modules ship.

## 7. States

- **Empty (module area) — the primary state for this spec slice:** illustration + copy + Add Module button, as above.
  This is the dominant state to design for right now.
- **Empty (member list):** not truly reachable — the creator/joiner is always a member from the moment the Home exists.
- **Loading:** a brief skeleton state on cold app launch while local Home + member data loads (should be near-instant
  per NFR-1, but a skeleton avoids a blank flash): skeleton header bar (gray blocks for name/badge), skeleton avatar row
  (3–4 gray circles), skeleton illustration area left blank/dimmed.
- **Error:** CORE-EXPERIENCE explicitly places generic app-level errors (storage/network/sync) out of scope for this
  spec. A minimal fallback is still specified here for resilience: a centered "Couldn't load your Home" message with a
  "Try Again" button, using the same tone as elsewhere ("Your household, your rules" — kept calm, not alarming). Flagged
  as an addition, not a spec requirement (§11).
- **Success:** fully populated header (correct name/shape), member row/panel showing all current members, empty module
  area with Add Module button active.

## 8. Theme Variants

| Theme             | Header / Shape Badge                                                                                                                                                                      | Member Avatar Chip                                                           | Empty illustration                                                                                                      | Add Module button                                                                              |
|-------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------|
| **Default**       | Soft-tint pill badge, 12dp radius                                                                                                                                                         | Standard circular avatar, small star icon for Admin                          | Simple flat/line illustration                                                                                           | Filled FAB, standard elevation                                                                 |
| **High Contrast** | Solid-fill badge with border (not a soft pastel tint) for legibility; role indicator shown as a text label chip ("Admin") rather than a small icon, since tiny icons can be imperceptible | Bold border ring around every avatar; role shown as text, not just an icon   | Simplified, high-contrast icon rather than detailed illustration                                                        | Larger (56dp+), bold single-color fill, no gradient                                            |
| **Kid-Friendly**  | Rounded, colorful badge, larger icon per shape                                                                                                                                            | Larger avatar chips, playful role badge (e.g., small crown emoji-style icon) | Warmer, more characterful illustration (e.g., a smiling house), optional subtle idle animation (respects reduce-motion) | Larger, extra-rounded, optional gentle pulse/bounce to invite tapping (respects reduce-motion) |

## 9. Accessibility Notes

- Focus order: Header (Home name/switcher) → Settings icon → Member avatar row/panel (each chip individually focusable,
  announced as "{Name}, {Admin/Member}, member of Home") → Invite tile/row → empty-state heading → Add Module button.
- Screen reader **announcement priority on screen entry:** announce Home name + member count first ("The Johnsons,
  Family home, 4 members") to orient the user, *before* reading the empty module state details — context before content.
- Touch targets: avatar chips minimum 44–48dp tap area even if the visual avatar circle renders smaller within that
  padded area.
- At 200% zoom: header wraps to two lines (name above; badge + switcher chevron + settings icon below); member avatar
  row remains horizontally scrollable rather than breaking into a broken grid; module empty-state stacks illustration
  above copy above button, with the illustration the first element to shrink or disappear if space is tight — text and
  the Add Module button remain primary.
- Home Switcher (both sheet and dropdown forms) traps focus while open and returns focus to the header chevron on
  dismiss.

## 10. Motion/Transition

- **Dashboard entry after Create/Join Home:** content fades/slides up into place with a slight stagger — header first,
  then member row, then module empty-state/CTA — guiding the eye down the intended priority order. This is the payoff of
  the Create/Join flows; it should feel like arrival, not a hard cut.
- **Switching Home (via Home Switcher):** does **not** feel like a full navigation push — the header updates instantly
  (new name/badge) while the body content (member list + module area) cross-fades to the new Home's data. This
  reinforces "same app, different Home," not "you've gone somewhere new."
- **Opening Member List on mobile:** slides up from bottom (treated as a modal-ish drill-in) or slides in from the right
  (treated as a stack push) — recommend slide-in-from-right, consistent with the "Members → S7" being a peer screen in
  the state table, not a transient overlay. (On tablet/desktop this is moot — the Members panel is always visible
  inline.)

## 11. Open Questions / Assumptions

- Whether a persistent **current-Profile indicator** (avatar/name) belongs in the Dashboard header is not specified in
  CORE-EXPERIENCE.md; recommended given the shared-device context (see `profile-switching.md` §10), needs sign-off.
- **Illustration style** for the empty module area — shared open question with `onboarding.md`.
- **Add Module as a persistent FAB** vs. an inline button that gets replaced later by an "+ Add" affordance within a
  populated module grid is a forward-looking recommendation, not something CORE-EXPERIENCE.md specifies (module flow is
  explicitly out of scope for that spec).
- **Loading skeleton and generic error/retry state** are additions beyond CORE-EXPERIENCE.md, which places generic
  app-level error states out of scope for this spec slice. Included here for completeness/resilience; needs confirmation
  these belong in this design pass or should be deferred to an architecture-level error-handling spec.
- **Exact breakpoint dp values** for compact/medium/expanded are not defined anywhere in the source docs; assumed
  standard Compose Multiplatform conventions.
- Whether the dashboard should show an explicit numeric "{N} members" label (for screen-reader/low-vision clarity beyond
  visually counting avatars) is assumed yes, but not explicitly specified.
