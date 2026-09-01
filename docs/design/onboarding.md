# Onboarding — Welcome, Profile Setup, Empty Home

> Visual/interaction design layer for the mandatory first-run path. Behavioral contract lives in CORE-EXPERIENCE.md
> §1–§2 and §6 State Model. This file describes how it looks and feels, not what it does.

## 1. Purpose & States Covered

| State ID | Screen        | CORE-EXPERIENCE ref           |
|----------|---------------|-------------------------------|
| `S1`     | Welcome       | §1 "Screen 1: Welcome"        |
| `S2`     | Profile Setup | §1 "Profile Setup (Screen 2)" |
| `S3`     | Empty Home    | §2 "Screen: Empty Home State" |

These three screens form one continuous forward path for a brand-new device: `S1 → S2 → S3`. They should feel like a
single unbroken corridor, not three separate "apps." A user should never feel like they've "arrived" until they reach
`S3`.

## 2. Target Platform & Responsive Behavior

- **Primary reference width:** phone portrait, ~360–420dp.
- **Layout strategy for all three screens:** a single centered content column, max-width capped at ~480dp even on larger
  viewports. These are identity/decision screens, not data screens — they never need multi-column layouts.
- **Tablet:** same single-column composition, centered horizontally with generous side margins (do not stretch the form
  or buttons edge-to-edge; a 420dp-wide card floating in the middle of a 900dp+ viewport reads as intentional, not
  broken).
- **Desktop:** identical centered-card treatment. Background outside the card may carry a subtle brand tint/texture (see
  Theme Variants) so the window doesn't feel empty, but no functional content lives outside the card.
- **Baseline spacing scale used throughout:** 4 / 8 / 12 / 16 / 24 / 32 / 48dp.

---

## 3. Screen: Welcome (`S1`)

### Layout (top → bottom)

```
┌─────────────────────────┐
│                         │  ← safe area
│        (spacer)         │
│      [ Vesta mark ]     │
│    Welcome to Vesta     │  ← H1
│  Your household, your   │  ← subtext, 2 lines max
│  rules. No account      │
│  needed.                │
│        (spacer)         │
│                         │
│   [   Get Started   ]   │  ← primary button, bottom-anchored
│                         │  ← safe area
└─────────────────────────┘
```

Content is vertically weighted toward the upper-middle third; the button is bottom-anchored (thumb zone on phones)
rather than immediately following the subtext. The gap between subtext and button is deliberate whitespace — it signals
"there is nothing else to read here."

### Component Inventory

| Component                          | Anatomy                                                                                                                               |
|------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------|
| **Wordmark/Logo Mark**             | Static icon (hearth/flame-in-house motif, per Vesta's namesake) + "Vesta" wordmark beneath or beside it. No animation on this screen. |
| **Heading Text**                   | Single line, H1 weight, centered.                                                                                                     |
| **Body Subtext**                   | 1–2 lines, secondary text color, centered.                                                                                            |
| **Primary Button ("Get Started")** | Filled, full-width on mobile with side margins; max-width ~340dp on tablet/desktop, centered.                                         |

### Visual Hierarchy & Emphasis

The **Primary Button** is the only actionable element on the screen, and design should make that unmistakable: it is the
only filled/high-contrast shape in an otherwise quiet, mostly-negative-space composition. There are no other links, no
"Sign In," no "Learn More" — the absence of alternatives *is* the hierarchy. This matches the spec's framing: Welcome
exists to establish trust by asking for nothing.

### States

- **Empty/Loading/Error:** not applicable — this is a static, offline, non-data screen. It renders instantly.
- **Success:** tap → forward transition to Profile Setup.

### Theme Variants

Welcome renders **before any Profile (and therefore any theme) exists.** Design decision: this screen always uses a
**neutral Default visual treatment**, but still respects **OS-level accessibility signals** that are device-wide, not
identity-specific (system dark/light mode, OS "bold text," OS "larger text," OS "reduce motion"). It does not attempt to
guess High Contrast or Kid-Friendly before anyone has told the app who they are.

| Theme context                           | Treatment at S1                                                                                                                                                     |
|-----------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Default (no OS accessibility flags)     | Standard palette, 16sp body, regular weight.                                                                                                                        |
| OS "increase contrast" / "bold text" on | Same layout, higher-contrast palette + bold text weight applied automatically — this is an OS signal, not a Vesta Profile theme, so it's safe to honor pre-Profile. |
| OS "reduce motion" on                   | Logo has no idle animation regardless; transition to S2 becomes a hard cut/fade instead of a slide.                                                                 |

### Accessibility Notes

- Screen reader announces heading first, then subtext, then button — in that order, on screen entry (matches visual
  top-to-bottom order, no reordering needed).
- Button touch target minimum 48×48dp regardless of visual button height.
- At 200% zoom: logo mark shrinks or hides first; heading and subtext reflow to additional lines; button remains
  full-width and reachable without horizontal scrolling.

### Motion/Transition

Entry: simple fade-in (this is the very first thing anyone sees — no directional bias needed). Exit (tap "Get Started"):
slide-in-from-right transition into Profile Setup, establishing the "moving forward" pattern that continues through S2 →
S3.

---

## 4. Screen: Profile Setup (`S2`)

### Layout (top → bottom)

```
┌─────────────────────────┐
│ [←]  (only if reached    │  ← back button, only present when
│       from Profile       │     entering via "Add Profile" (S1b)
│       Picker)             │
│                          │
│      ( Avatar circle )   │  ← tap to open Avatar Picker
│       Choose avatar       │
│                          │
│  Display Name            │  ← required field, most prominent input
│  [______________]        │
│  2–40 characters          │
│                          │
│  App Theme (optional)     │
│  [Default] [HC] [Kid]     │  ← 3 selectable theme cards
│                          │
│   [      Continue      ]  │  ← bottom-anchored, disabled until valid
└─────────────────────────┘
```

### Component Inventory

| Component                       | Anatomy                                                                                                                                                                                        |
|---------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Avatar Preview Circle**       | Large circular preview (~96dp mobile / ~120dp tablet+), placeholder silhouette if unselected, small pencil/edit badge overlay bottom-right.                                                    |
| **Avatar Picker Sheet**         | Modal grid of preset avatar tiles (illustrated faces/objects), single-select, tap a tile to select + auto-close.                                                                               |
| **Text Input ("Display Name")** | Label above, input field, live character counter (e.g. "3/40"), inline error text below on invalid submit attempt.                                                                             |
| **Theme Selector Card**         | 3 cards, each with theme name + a small preview swatch (mini color chips / sample "Aa" text at that theme's weight/size); selected state shown via ring/border + checkmark, never color alone. |
| **Primary Button ("Continue")** | Disabled (muted, non-interactive) until name is 2–40 chars; enabled state is filled/high-contrast.                                                                                             |

### Visual Hierarchy & Emphasis

**Display Name** is the single most prominent element — it's the only field that gates progress, so it should be the
largest, topmost interactive element in the form body, with the only implied "required" treatment (no literal asterisk
needed; the disabled button communicates requirement). Avatar and Theme are deliberately smaller and visually quieter
(muted borders, smaller type) until touched — this visually reinforces the spec's opinionated decision that they are
skippable, not just technically optional.

### States

- **Empty:** name field blank, Continue disabled, avatar shows placeholder, theme selector pre-highlights the
  system-detected preference (per spec: "Defaults to system preference if available") without forcing a selection.
- **Loading:** none — all local, instant.
- **Error (inline validation):** appears only after blur or a submit attempt, never on every keystroke. Error text sits
  directly under the field, in the error color, with an icon; the field border also shifts to the error color (never
  color-only — text is always present).
- **Success:** tap Continue → brief confirm micro-state (button shows a checkmark for ~200ms) → transition to Empty
  Home.

### Theme Variants

Setup is where a theme gets *chosen*, so the live preview matters more than the form's own chrome. Design decision: as
soon as the user taps a Theme Selector Card, the **entire screen re-skins live** (colors, corner radius, font weight) so
the choice is felt immediately, not just described.

| Theme             | Visual signature applied live                                                                                                              | Baseline touch target | Corner radius                     | Type                                                         |
|-------------------|--------------------------------------------------------------------------------------------------------------------------------------------|-----------------------|-----------------------------------|--------------------------------------------------------------|
| **Default**       | Neutral palette, moderate contrast                                                                                                         | 48dp                  | 12dp                              | System font, regular/medium weight, 16sp body                |
| **High Contrast** | Limited high-contrast palette (e.g. near-black/near-white/one accent), selection state shown via bold border + text label, not color alone | 56dp                  | 8dp (squarer, more "solid" edges) | System font, semibold/bold, 18sp+ body, target ≥7:1 contrast |
| **Kid-Friendly**  | Warmer palette, more illustrated texture, larger imagery on avatar tiles                                                                   | 56–64dp               | 20–24dp (very rounded)            | Rounded/friendly typeface, medium-bold, 18–20sp body         |

### Accessibility Notes

- Theme Selector Cards behave as a radio group: only one selectable, arrow-key navigable on desktop/keyboard, announced
  as "App theme, 3 options, Default selected."
- Avatar Picker Sheet traps focus while open; closing returns focus to the Avatar Preview Circle.
- Focus order: (Back if present) → Avatar Preview → Display Name → Theme cards → Continue.
- At 200% zoom: Theme Selector Cards stack vertically (from a row) rather than truncating labels.

### Motion/Transition

Entry: slide-in-from-right (continuing the forward pattern from S1), or slide-up if entered from the Profile Picker's
"Add Profile" (a distinct enough context that a vertical entrance differentiates "adding to a list" from "linear
progress"). Theme selection triggers an immediate cross-fade re-skin of the whole screen (~150–200ms), not a hard flash.
Exit on submit: fields visually "settle"/collapse briefly before the Empty Home screen fades/slides in — this reads as
completion, not reset.

---

## 5. Screen: Empty Home (`S3`)

### Layout (top → bottom)

```
┌─────────────────────────┐
│  Welcome, Sarah          │  ← greeting, H1
│                          │
│      ( illustration )    │  ← decision-point graphic
│                          │
│  You're not in a Home    │
│  yet. Create one or      │
│  join one.               │
│                          │
│  [   Create a Home   ]   │  ← primary, filled
│                          │
│  [   Join a Home     ]   │  ← secondary, outlined
└─────────────────────────┘
```

### Component Inventory

| Component                            | Anatomy                                                                                                                                          |
|--------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------|
| **Greeting Header Text**             | "Welcome, {name}" — H1, left- or center-aligned, top of content area. No icons, no chrome.                                                       |
| **Decision Illustration**            | A single graphic representing a fork/choice (e.g. two doors, or a house outline awaiting definition) — not a busy scene, a simple symbolic mark. |
| **Subtext**                          | 1–2 lines, secondary color.                                                                                                                      |
| **Primary Button ("Create a Home")** | Filled, full-width, positioned first (top) in the action stack.                                                                                  |
| **Secondary Button ("Join a Home")** | Outlined or text-style, full-width, same height/touch-target as the primary button, positioned second.                                           |

### Visual Hierarchy & Emphasis

**"Create a Home"** is the single most prominent element on the screen — filled/solid styling and top position in the
action stack, because creating fresh is the more common first path for a brand-new device. "Join a Home" is equal in
**size** (per the spec's explicit accessibility note: "two large, clearly separated touch targets") but visually quieter
in **style** (outline, not fill) — size parity avoids penalizing joiners; style difference still communicates a
primary/secondary relationship.

> **Design decision (ties to CORE-EXPERIENCE §2 opinionated decision):** this screen intentionally has zero dashboard
> chrome — no member list preview, no module gallery, no settings icon. It is rendered as a **decision point**, visually
> closer to a modal-style choice screen than to the eventual Home Dashboard it will be replaced by. Nothing here should
> visually resemble the dashboard, so the contrast between "no Home" and "has Home" is unmistakable at a glance.

### States

- **Empty:** this screen *is* the empty state — there's no further-empty variant to design.
- **Loading:** none — the greeting and buttons render instantly from the just-created Profile.
- **Error:** none defined; both actions simply navigate forward.
- **Success:** tap either button → forward transition into the respective form (`S4` or `S5`).

### Theme Variants

By this point a Profile (and its theme) exists, so this screen renders fully themed.

| Theme             | Illustration style                                                                                                                                                                                                             | Button treatment                                                                                                         |
|-------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------|
| **Default**       | Simple line/flat illustration, muted accent color                                                                                                                                                                              | Standard filled/outline pair                                                                                             |
| **High Contrast** | Illustration simplified further or reduced to a bold icon (avoid low-contrast decorative linework); both buttons get bold, high-contrast borders so the "outline" secondary button remains clearly perceivable, not just faint | Larger (56dp+) targets, generous vertical gap between the two buttons to avoid mis-taps                                  |
| **Kid-Friendly**  | Warmer, more characterful illustration (e.g., a friendly house character)                                                                                                                                                      | Rounded corners, slightly larger buttons, optional gentle idle animation on the illustration (respects OS reduce-motion) |

### Accessibility Notes

- Focus order: Greeting (announced but not focusable) → Create a Home → Join a Home.
- The two buttons must have a clear, generous gap (minimum 16dp, more in High Contrast/Kid themes) — this is explicit in
  CORE-EXPERIENCE §2 ("clearly separated touch targets") and is an anti-mis-tap measure for both Arthur (motor
  precision) and young children.
- At 200% zoom: illustration is the first element to shrink/hide; heading, subtext, and both buttons remain fully
  visible without horizontal scroll.

### Motion/Transition

Entry from Profile Setup: content slides/fades up into place, with the greeting appearing fractionally before the
buttons (small stagger, ~80ms) so the eye reads "welcome" before "choose." This is the payoff moment of onboarding — it
should feel like arrival, not a reset. Exit: slide-in-from-right into whichever form was tapped.

---

## 6. Open Questions / Assumptions

- **Illustration style/asset library** is undefined in CORE-EXPERIENCE.md. Assumed a simple, brand-consistent flat/line
  illustration set (hearth/house motifs) exists or needs commissioning; not specified which.
- **Avatar preset count and art style** ("curated set of presets") is not specified — number of presets, illustration
  style (flat icons vs. characters vs. photographic), and whether presets vary by theme (e.g., a Kid-Friendly-flavored
  preset set) all need a decision before a renderer can produce the Avatar Picker.
- **Pre-Profile theme handling at S1/S2** (neutral Default + OS-signal-only) is a design assumption, not stated in
  CORE-EXPERIENCE.md. Needs confirmation this doesn't conflict with any existing platform-level theming architecture.
- **Logo/wordmark asset** does not exist yet in the provided docs; assumed a hearth/flame-in-house motif based on the
  product's Vesta (Roman hearth goddess) naming, per product/README.md framing.
- Whether the Empty Home screen should show any minimal profile-identity chip (e.g., "not Sarah? switch profile") is not
  specified in CORE-EXPERIENCE.md §2 and was deliberately **not added**, to respect the "no settings, no chrome" rule —
  flagged here in case product wants a lightweight escape hatch for shared devices.
