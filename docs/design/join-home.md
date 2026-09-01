# Join Home Form, Confirmation & Error States

> Visual/interaction design layer for joining an existing Home via invite code. Behavioral contract: CORE-EXPERIENCE.md
> §4 "Joining a Home" and §6 State Model (`S5`, `S10`, `S11`).

## 1. Purpose & States Covered

| State ID | Screen            | CORE-EXPERIENCE ref                        |
|----------|-------------------|--------------------------------------------|
| `S5`     | Join Home Form    | §4 "Joiner Experience (Screen: Join Home)" |
| `S10`    | Join Confirmation | §4 "On valid code"                         |
| `S11`    | Join Error        | §4 "On invalid/expired code"               |

These three states are tightly coupled and, per the design reasoning below, are rendered as **one screen (`S5`) with two
possible overlay states (`S10` confirmation, `S11` error)** rather than three distinct full-screen pushes.

## 2. Target Platform & Responsive Behavior

- **Mobile (~360–420dp):** single column, centered code-entry as the dominant element.
- **Tablet/Desktop:** identical composition, centered in a max-width ~420–480dp card — this is a short, linear,
  code-entry task with no benefit from extra width; no multi-column treatment needed.
- Confirmation and Error states render as **overlays** (bottom sheet / dialog) on top of the Join Form rather than as
  separate full-screen navigations, on both mobile and larger viewports — see §7 for the reasoning.

## 3. Layout — Join Home Form (`S5`)

```
┌─────────────────────────────┐
│ [←]  Join a Home              │  ← back button + title
│                              │
│  Enter the 6-digit code your  │  ← instruction microcopy
│  Home admin shared with you.  │
│                              │
│   [4][8][2]  [9][1][7]        │  ← code input, 2 clusters of 3
│                              │
│                              │
│   [       Join       ]        │  ← disabled until 6 digits entered
└─────────────────────────────┘
```

The 3+3 grouping of the code input **mirrors the inviter's own display of the code** ("482 917," per CORE-EXPERIENCE
§4's Inviter Experience table) — the visual pattern the joiner is asked to type matches exactly what they were shown,
reducing transcription errors.

## 4. Component Inventory

| Component                      | Anatomy                                                                                                                                                                                                                                         |
|--------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Instruction Text**           | 1–2 lines above the code input, authored microcopy (not verbatim in the source spec — see Open Questions).                                                                                                                                      |
| **Code Input Segment Group**   | Six individual boxes in two visual clusters of three, each holding one digit; auto-advances focus on entry, backspace moves focus to the previous box; filled box shows a subtle highlight/border change. Numeric keyboard triggered on mobile. |
| **Primary Button ("Join")**    | Disabled until all 6 digits are entered; enabled state filled/high-contrast.                                                                                                                                                                    |
| **Back Button**                | Top-left, returns to Empty Home or Home Switcher depending on entry point.                                                                                                                                                                      |
| **Confirmation Sheet** (`S10`) | Bottom sheet overlay with scrim: Home name (large), Shape Badge, confirmation question text ("Join **Flat 4B** (Flat Share)?"), Confirm button (primary), Cancel button (text/secondary).                                                       |
| **Error Alert Dialog** (`S11`) | Centered modal dialog: neutral (not alarm-red) warning icon, message text (per the exact copy variants in CORE-EXPERIENCE §4's error table), single "Try Again" dismiss button.                                                                 |
| **"Already a member" Toast**   | A distinct, lighter-weight treatment than the Error Dialog — see §7 — a transient toast/snackbar that auto-navigates to the Dashboard rather than blocking with a dismiss action.                                                               |

## 5. Visual Hierarchy & Emphasis

The **Code Input** is singularly dominant on this screen — the largest, most central, most visually active element, with
everything else (instruction text, button) framing it. This mirrors near-universal OTP-entry conventions and directly
serves persona needs: Arthur typing a shared code needs large, unambiguous, well-spaced boxes; a child joining a family
Home benefits equally from big, forgiving tap targets.

## 6. States

- **Empty:** no digits entered, first box focused and ready for input, numeric keypad shown on mobile, Join button
  disabled.
- **Loading ("Checking code…"):** after the 6th digit is entered, a brief inline validation state — the Join button
  shows a spinner in place of its label, and the code boxes lock (non-editable) during the check. CORE-EXPERIENCE.md
  does not specify code-validation latency; this state is included defensively in case validation involves more than a
  purely local, instant lookup (architecture-dependent — see Open Questions).
- **Error (`S11`):** modal Error Alert Dialog, per the exact copy variants defined in CORE-EXPERIENCE §4 (code not
  found / code expired / already a member / joining own Home). Icon and tone are **neutral, not alarming** — these are
  expected, common user mistakes (a mistyped digit, an expired code), not system failures.
- **Success → Confirmation (`S10`):** Confirmation Sheet slides up over a dimmed Join Form background — the user can
  still see they're "in" the join flow, just confirming a final step, not being sent somewhere new.
- **Confirmed → Dashboard:** sheet dismisses, Dashboard loads with a brief toast ("You joined Flat 4B!"), new member's
  role badge shows "Member" (never "Admin").

## 7. A Specific Design Reasoning: Why Error Is a Modal, Not an Inline Banner

The State Model in CORE-EXPERIENCE §6 gives `S11` its own state ID with an explicit exit action, **"Dismiss → `S5`."** A
passive inline banner or an auto-dismissing toast wouldn't need a modeled "Dismiss" action — that phrasing implies a
state the user must actively acknowledge. Design conclusion: **Join Error is a modal Alert Dialog requiring an explicit
tap to dismiss**, not a quietly-appearing inline error message.

**Exception — "Already a member":** per CORE-EXPERIENCE §4, this case doesn't ask the user to fix anything; it
explicitly says "Redirect to Home Dashboard." Design conclusion: this specific case is **not** a blocking dialog but a
brief, lighter **toast** ("You're already in this Home") that appears and then auto-navigates to the Dashboard — the one
error variant that behaves like a soft redirect rather than a blocking error. This distinction is proposed here since
the source spec's error table presents identical formatting for all four variants and doesn't explicitly differentiate
interaction pattern — flagged in Open Questions.

## 8. Theme Variants

| Theme             | Code Input boxes                                                                                                                      | Error Dialog                                                                                                                                                                |
|-------------------|---------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Default**       | Standard size (~48×56dp per box), 12dp radius, regular-weight digits                                                                  | Standard modal, neutral icon color                                                                                                                                          |
| **High Contrast** | Larger boxes (~56×64dp+), bold borders, bold/larger digit font — numeric legibility is critical for both elderly and low-vision users | Bold border, higher-contrast icon, message text bold/18sp+                                                                                                                  |
| **Kid-Friendly**  | Larger boxes, rounded corners, optional small "ding" micro-animation per digit entered (respects reduce-motion)                       | Warmer tone, friendlier icon; a small, tasteful celebratory animation on successful confirm is a *proposed* addition (see Open Questions), not specified in the source spec |

## 9. Accessibility Notes

- **OTP auto-advance and screen readers is a known accessibility pitfall** (NFR-20/21): silent auto-advance-on-type can
  fight with assistive tech focus announcements. Recommended treatment: when a screen reader is active, either (a)
  announce "Digit {n} of 6 entered, moved to digit {n+1}" on each auto-advance, or (b) offer a single accessible text
  field with clear formatting guidance as a fallback input mode. This needs validation with real assistive-tech testing
  before implementation.
- Each Code Input box is individually labeled for screen readers: "Digit 1 of 6," etc.
- Confirmation Sheet and Error Dialog both trap focus while open. On dismiss: the Error Dialog returns focus to the
  first Code Input box (cleared or highlighted for correction); the Confirmation Sheet's "Cancel" returns focus to the
  Join button area on `S5`.
- At 200% zoom: Code Input boxes remain in their 3+3 grouping but may wrap to two full rows rather than shrinking below
  a legible minimum size.

## 10. Motion/Transition

- **Join Form entry:** slide-in-from-right, consistent with Create Home's forward-progress pattern.
- **Confirmation Sheet:** slides up from the bottom (modal convention — signals "temporary, easy to dismiss downward").
- **Error Dialog:** center-scale-in (distinct from the sheet's slide-up, to differentiate "confirming forward progress"
  from "something needs your attention").
- **Successful join:** the Confirmation Sheet dismisses downward while the Dashboard cross-fades in beneath it — the
  same continuity principle used in `create-home.md`.

## 11. Open Questions / Assumptions

- **Confirmation as a bottom sheet vs. a full dialog/screen** is a design choice, not explicitly specified; reasoning
  given in §3/§7, needs confirmation.
- **Error as a modal dialog** (vs. inline banner) is inferred from the state table's explicit "Dismiss" exit action for
  `S11` — flagged here specifically because it's an inference, not a stated instruction, and should be confirmed with
  product before implementation.
- **"Already a member" as an auto-redirect toast** rather than a blocking dialog is a proposed interaction distinction
  not explicit in the source spec's error table (which formats all four variants identically); needs confirmation.
- **Code validation latency / the "Checking code…" loading state**: whether validation is always instant (purely local)
  or can require P2P peer resolution with real latency is an architecture question outside this spec's scope; the
  loading state is included defensively.
- **Instruction microcopy** above the code input ("Enter the 6-digit code your Home admin shared with you") is authored
  fresh for this design pass — it does not appear verbatim in CORE-EXPERIENCE.md — and should go through a copy review
  pass to confirm tone consistency ("Your household, your rules.").
- **Kid-Friendly celebratory micro-animation on successful join** is a proposed addition beyond the source spec, not a
  confirmed requirement — flagged for sign-off given NON-GOALS.md's caution against gamification-adjacent features
  creeping into core (this is presented as a subtle, tasteful animation, not gamification, but the line is worth product
  confirming explicitly).
