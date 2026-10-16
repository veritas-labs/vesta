# Create Home Form

> Visual/interaction design layer for Home creation and its transition into the Dashboard. Behavioral contract:
> CORE-EXPERIENCE.md §3 "Creating a Home" and §6 State Model (`S4` → `S6`).

## 1. Purpose & States Covered

| State ID                 | Screen           | CORE-EXPERIENCE ref         |
|--------------------------|------------------|-----------------------------|
| `S4`                     | Create Home Form | §3 "Flow: Create Home Form" |
| *(transition into)* `S6` | Home Dashboard   | §3 "Success State"          |

A single-screen form, not a wizard — this is the load-bearing design constraint for the whole file. Everything below
assumes one scroll-free (or minimally-scrolling) screen with all fields visible at once.

## 2. Target Platform & Responsive Behavior

- **Mobile (~360–420dp):** single column, vertical stack, Shape options rendered as a **vertical list** of selectable
  rows (icon + label + one-line description) — chosen over a 2×2 icon grid because the shape descriptions are a full
  sentence each and read better left-aligned in a row than squeezed under a small icon.
- **Tablet/Desktop (≥600dp): Shape options switch to a 2×2 grid** of larger selectable cards — more horizontal room
  makes a grid read as a deliberate, scannable choice rather than a cramped list. This is the specific tablet/desktop
  divergence for this screen.
- Form max-width capped ~480–560dp even on wide viewports, centered, consistent with the onboarding screens'
  single-column philosophy — this is a short, linear form, not a data-dense screen.

## 3. Layout (top → bottom, mobile)

```
┌─────────────────────────────┐
│ [←]  Create a Home           │  ← back button + title
│                              │
│  Home Name                   │
│  [______________________]    │  ← e.g. "The Johnsons", "Flat 4B"
│  2–50 characters              │
│                              │
│  Home Shape                  │
│  ○ Family                    │  ← selectable row, icon+label+desc
│  ○ Flat Share                 │
│  ○ Single + Support            │
│  ○ Other                      │
│                              │
│  ⓘ You'll be the admin of      │  ← informational note, non-interactive
│    this Home.                  │
│                              │
│   [     Create Home     ]     │  ← bottom-anchored, disabled until valid
└─────────────────────────────┘
```

## 4. Component Inventory

| Component                                         | Anatomy                                                                                                                                                                                                                                                                                                      |
|---------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Text Input ("Home Name")**                      | Label above, input field, helper caption below showing examples ("e.g. The Johnsons, Flat 4B, Mum's Place") rather than placeholder-only text (placeholders disappear on typing and hurt discoverability for novice users like Arthur), live character counter, inline error text on invalid submit attempt. |
| **Shape Option Row/Card**                         | Icon (one per shape — house/people/heart-hands/asterisk, pending brand assets) + label + one-line description + selection indicator (radio-style circle or checkmark). Mobile = full-width row; tablet/desktop = square-ish card in a 2×2 grid.                                                              |
| **Shape Option Group**                            | Radio-group container of exactly 4 options, always all visible (never collapsed behind a "more" disclosure).                                                                                                                                                                                                 |
| **Informational Note ("You'll be the admin...")** | Non-interactive, muted background chip or plain text with a small info icon — styled distinctly from the form fields so it reads as automatic/system-set information, not something the user is meant to act on.                                                                                             |
| **Primary Button ("Create Home")**                | Disabled/muted until Home Name is valid (2–50 chars) AND a Shape is selected; enabled state is filled/high-contrast.                                                                                                                                                                                         |
| **Back Button**                                   | Top-left icon button, returns to Empty Home or Home Switcher depending on entry point.                                                                                                                                                                                                                       |

## 5. Visual Hierarchy & Emphasis

**Home Name** is the entry point — the first thing the eye and cursor land on, and the field that personalizes the Home
immediately. **Home Shape**, however, occupies the most vertical space and interactive richness on the screen, and
should be treated as the primary *visual weight* of the screen: it's the harder, more consequential decision (per
CORE-EXPERIENCE §3, shape describes the Home to its members (it does not choose tools) and is deliberately required at creation
despite the added friction). Design should give the Shape options generous spacing and clear selection affordance — this
is the one decision on the screen worth slowing down for, even though the whole flow should still complete in seconds.

The **informational note** ("You'll be the admin...") is deliberately the *least* visually active element — no input, no
interaction, easy to skim past — because it communicates a fact, not a choice.

## 6. States

- **Empty:** Home Name blank, no Shape selected, Create Home button disabled.
- **Validation error:** Home Name too short/long — inline error text under the field, shown only after blur or a submit
  attempt (never mid-keystroke), field border shifts to the error color alongside the text (never color alone).
- **Loading:** none functionally required — Home creation is local and effectively instant (NFR-1). A very brief (<
  300ms) button-press "Creating…" micro-state (spinner replacing the button label) is still specified purely as tactile
  feedback and to prevent accidental double-submission.
- **Success:** on submit, the form visually "settles" (fields fade/collapse) as the Dashboard fades in beneath it. Per
  CORE-EXPERIENCE's target of ≤60 seconds total and the emphasis on speed, there is **no separate full-screen
  success/celebration screen** — instead, a brief confirmation toast/snackbar (e.g., "Flat 4B created!") appears on the
  Dashboard as it loads, rather than blocking forward progress with an interstitial.
- **Error (system-level, e.g. local storage failure):** not covered by CORE-EXPERIENCE.md (explicitly out of scope). A
  minimal fallback: inline banner above the Create Home button, "Something went wrong. Try again." with the form state
  preserved (nothing the user typed is lost).

## 7. Theme Variants

| Theme             | Shape Option treatment                                                                                  | Selection indicator                                                                                                                                                |
|-------------------|---------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Default**       | Standard card/row with soft border, 12dp radius                                                         | Filled radio dot + border highlight                                                                                                                                |
| **High Contrast** | Bold outline on every option (not just selected), 8dp radius, larger icons                              | Selected state shown via a **text label ("Selected")** in addition to the visual highlight — color/border alone is not sufficient for High Contrast users (NFR-22) |
| **Kid-Friendly**  | Bigger, friendlier icons per shape (e.g., a smiling house for "Family"), 20–24dp radius, warmer palette | Checkmark badge with a small bounce-in animation on selection (respects reduce-motion)                                                                             |

## 8. Accessibility Notes

- Home Shape is a true radio-group: only one selectable at a time, arrow-key navigable on desktop/keyboard, announced as
  "Home shape, radio button group, 4 options, {current selection or 'none selected'}."
- Focus order: Back button → Home Name field → Shape options in the order listed (Family, Flat Share, Single + Support,
  Other) → informational note (announced once, but not a focus stop since it's non-interactive) → Create Home button.
- At 200% zoom: Shape cards (tablet/desktop 2×2 grid) collapse to the mobile vertical-list treatment regardless of
  viewport, since a 2×2 grid cannot readably fit at that zoom level; description text wraps rather than truncating.
- Character counters on Home Name are announced by screen readers only on field blur, not on every keystroke, to avoid
  overwhelming verbosity.

## 9. Motion/Transition

Entry from Empty Home: slide-in-from-right, consistent with the onboarding "forward progress" pattern. On successful
submit: rather than a jarring hard-cut to the Dashboard, the form fields collapse/fade while the Dashboard content fades
in underneath — the Home Name and Shape the user just described visually "become" the Dashboard's header, giving a sense
of continuity ("the thing I just described is now real") rather than a screen swap.

## 10. Open Questions / Assumptions

- **Success confirmation as a toast/snackbar on the Dashboard** (vs. a distinct success micro-screen) is a design
  recommendation prioritizing speed per NFR-40/the ≤60s target in CORE-EXPERIENCE §3; not explicitly specified either
  way.
- **Icon per Home Shape** (house / people / heart-hands / asterisk-or-similar for "Other") is an assumption —
  CORE-EXPERIENCE.md does not define shape iconography; pending brand asset creation.
- **Home Name helper caption vs. placeholder text**: assumed a persistent helper caption below the field (showing the
  spec's example names) rather than disappearing placeholder text, for better discoverability for novice users; not
  specified in the source spec.
- Whether the four Shape options should ever collapse behind a "more" disclosure (e.g. hiding "Other") — assumed **no**,
  all four always visible per the spec's explicit "deliberately short" framing; flagged only to confirm this isn't
  reconsidered later for visual minimalism.
