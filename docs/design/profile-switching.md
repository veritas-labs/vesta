# Profile Picker & Identity Switching

> Visual/interaction design layer for the shared-device Profile resolution screen and the broader act of switching
> identity/theme. Behavioral contract: CORE-EXPERIENCE.md §1 "Profiles & Shared Devices (Screen 1b)" and §6 State Model
> (`S1b`).

## 1. Purpose & States Covered

| State ID | Screen         | CORE-EXPERIENCE ref |
|----------|----------------|---------------------|
| `S1b`    | Profile Picker | §1 "Screen 1b"      |

This screen exists specifically for **shared devices** (a family tablet, a kitchen device) — it is the answer to "who is
holding this device right now?" It is shown whenever there is ≥1 Profile and no active session, which includes both cold
app launches on a returning device and (per this file's design extension, see §10) manual mid-session profile switches.

## 2. Target Platform & Responsive Behavior

This is the one screen in the entry-experience set where **tablet is not a secondary consideration — it's the primary
use case.** A family tablet may host 4–8 Profiles; a single-user phone may host exactly 1.

| Viewport             | Grid density                                                                                                                           |
|----------------------|----------------------------------------------------------------------------------------------------------------------------------------|
| Mobile (~360–420dp)  | Single column, stacked Profile Cards (list-like), full-width rows                                                                      |
| Tablet (~600–1024dp) | 3–4 column grid, square-ish Profile Cards, centered with margins                                                                       |
| Desktop (≥1024dp)    | 4–6 column grid, max-width container (~960dp) centered in window, remaining background is ambient (subtle tint, no functional content) |

Grid reflows are a straightforward column-count responsive breakpoint — no divergent information architecture between
form factors, only density.

## 3. Layout (top → bottom)

```
┌───────────────────────────────┐
│         (Vesta mark, small)    │
│      Who's using Vesta?        │  ← H1, centered
│                                │
│  [Avatar] [Avatar] [Avatar]    │  ← Profile Card grid
│   Sarah    Arthur    Mei       │     (columns per breakpoint)
│                                │
│  [ + ]                        │  ← Add Profile tile, styled distinctly
│  Add Profile                   │     (dashed border, not a "real" card)
└───────────────────────────────┘
```

No header chrome beyond the small logo mark and heading — no settings icon, no back button (this is a root-level
resolution screen, not a step in a stack).

## 4. Component Inventory

| Component                 | Anatomy                                                                                                                                                                                      |
|---------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Profile Card**          | Circular or rounded-square avatar image (~96dp mobile / ~120–140dp tablet+), name label beneath, subtle elevation/border. Tap = select.                                                      |
| **"Last used" indicator** | A subtle ring or small dot on the Profile Card belonging to whichever Profile was active most recently on this device (see Open Questions — this is a proposed addition, not spec-mandated). |
| **Add Profile Tile**      | Same footprint as a Profile Card but visually distinct: dashed outline, "+" icon, muted color, label "Add Profile" — deliberately *not* styled like a real person's card.                    |
| **Grid Container**        | Responsive column count per breakpoint (see §2), consistent gutter (16–24dp).                                                                                                                |

## 5. Visual Hierarchy & Emphasis

**No single Profile Card is emphasized over any other** — this is a deliberately egalitarian screen. The most prominent
element is the **heading question itself** ("Who's using Vesta?"), which frames the grid as a body of equal,
undifferentiated choices. This matters for a device shared across ages 8–100: the app must never visually suggest a
"default" identity (e.g., always highlighting the adult's card first), because a child, a grandkid, or a helper may be
the one picking up the tablet next.

> **Design decision:** the "Add Profile" tile is visually the *least* prominent tile in the grid (muted, dashed, no
> photographic weight) so it never gets mistaken for a real person's card in a fast-tap scenario — an important
> consideration for younger or lower-vision users.

## 6. States

- **Empty:** not practically reachable — `S1b`'s entry condition requires ≥1 Profile to exist (0 Profiles routes to `S1`
  Welcome instead). If Profile deletion is added later and could reach 0 Profiles from this screen, that's out of scope
  here (Profile editing/deletion is explicitly out of scope per CORE-EXPERIENCE §8).
- **Loading:** none — Profiles are read from local storage, effectively instant (NFR-1).
- **Error:** none defined at this state.
- **Success:** tap a Profile Card → brief scale/highlight confirmation on that card → theme cross-fade (see §9 Motion) →
  transition to `S3` (no Homes) or `S6` (has Home), per the Profile's membership.

## 7. Theme Variants — and a Specific Design Recommendation

Because multiple Profiles with **different themes** can exist on one device, this screen cannot simply "be" any one
Profile's theme by default — until a card is tapped, the app doesn't know who's using it.

**Default behavior (multi-Profile device):** render the Picker in a **neutral, Default-leaning style**, but still honor
OS-level accessibility signals (bold text, increase contrast, reduce motion) exactly as described for the pre-Profile
screens in `onboarding.md` §3.

> **Recommended design decision (needs product sign-off — see §11):** when a device has **exactly one Profile**, there
> is no identity ambiguity — the Picker should render immediately in *that* Profile's theme, rather than a neutral
> Default. This directly serves Arthur: on his own single-Profile tablet, he should never see a low-contrast, small-text
> screen before reaching his own High Contrast settings. This fast-path only applies to single-Profile devices;
> multi-Profile devices remain neutral until selection to avoid assuming identity.

| Theme (as applied post-fast-path or after selection) | Profile Card treatment                                                                                                                                                                                                          |
|------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Default**                                          | Standard elevation/shadow, 12dp corner radius, 16sp name label                                                                                                                                                                  |
| **High Contrast**                                    | Bold border around every card (not just selected state), 8dp corner radius, name label bold/18sp+, "last used" indicator shown as a text tag ("Last used") rather than a small dot — dots are too low-contrast/small to rely on |
| **Kid-Friendly**                                     | Larger cards, 20–24dp corner radius, more colorful/character-forward avatar rendering, name label in rounded typeface                                                                                                           |

## 8. Accessibility Notes

- Touch targets: Profile Cards minimum 48×48dp (Default) up to 64dp (Kid-Friendly/High Contrast device context) — but
  note the *device* context here may not be knowable pre-selection on multi-Profile devices, so default to the larger,
  safer 56dp baseline for this screen regardless of eventual per-Profile theme, given its shared-device, mixed-audience
  nature.
- Screen reader announces, per card: "{Name}, profile, button" and, for the grid overall on entry: "Profile picker. {N}
  profiles available."
- Focus order: heading (announced, not focusable) → Profile Cards in visual reading order (left-to-right,
  top-to-bottom) → Add Profile tile last.
- At 200% zoom: grid collapses to a single column regardless of viewport; cards become full-width rows with the avatar
  on the left and name label wrapping beside it (never truncated).

## 9. Motion/Transition

- **Cold launch entry:** simple fade-in — this may be the very first screen a given family member sees today, so no
  directional bias.
- **Mid-session "Switch Profile" entry** (see §10 below): a distinct downward slide or cross-fade from the current
  screen, signaling "stepping out" of the current identity, rather than a forward "push," to differentiate it from
  normal in-app navigation.
- **Selecting a card:** brief scale-pulse on the tapped card, then a **theme cross-fade** across the whole screen
  (colors/typography swapping to the selected Profile's theme, ~200–250ms) before navigating onward — this is the moment
  the spec's rule "switching Profile switches the app's identity and theme" becomes visible and felt, not just true in
  data.

## 10. The Act of Switching Mid-Session

CORE-EXPERIENCE.md defines `S1b` only as a **launch-time** resolution screen (entry condition: "no active session"). It
does not explicitly define an in-app entry point for switching Profile *during* an active session (e.g., handing the
tablet to another family member without closing the app). Given FR-46 ("Switching Profile switches the current app user,
including their theme") and the shared-device use case, some entry point is very likely needed in practice.

**Assumed design (flagged for sign-off):** a small **Profile Avatar Chip** in the Home Dashboard header (top-left or
top-right, opposite the Settings icon) that, when tapped, opens a lightweight menu with a single "Switch Profile"
action, returning the user to `S1b`. This is proposed here rather than asserted as settled, since it isn't in the source
spec.

## 11. Open Questions / Assumptions

- **In-app "Switch Profile" entry point** (avatar chip vs. Settings menu item vs. something else) is not defined in
  CORE-EXPERIENCE.md; proposed above, needs product confirmation.
- **"Last used" visual indicator** on Profile Cards is a proposed addition for returning-user efficiency, not specified
  in the source spec.
- **Add Profile as an in-grid tile** (vs. a separate button below the grid) is a layout assumption; both are plausible,
  in-grid was chosen for visual consistency of the tap pattern.
- **Single-Profile theme fast-path** (§7) is the most consequential recommendation in this file — it materially affects
  Arthur's first impression on his own device and should be explicitly confirmed or rejected by product before
  implementation.
- **Avatar asset style/count** — shared open question with `onboarding.md`.
