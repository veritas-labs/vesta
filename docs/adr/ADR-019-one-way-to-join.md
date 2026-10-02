# ADR-019: One way to join a Home — the invite link

**Status:** Proposed (2026-10-02). Not implemented. Supersedes the mechanism of [[ADR-015]] (the joiner's short-lived join
link) and the "keep both flows" decision of [[ADR-018]]. Everything else in [[ADR-018]] stands.

## Context

[[ADR-018]] added invite links beside [[ADR-015]]'s join link, on the argument that the join link carries no secret. In
use the two paths are confusing: the screens give a person no way to tell which to choose, and the Join a home screen
shows a QR code the user must ignore if they hold an invite link. The secret-free property matters little, because an
invite link cannot admit anyone without an admin's approval ([[ADR-018]] step 4), and it expires and is single-use.
Someone standing next to the admin is covered too: the admin shows the link's QR code, the joiner scans it, and the admin
taps Approve.

## Decision

**Invite links are the only way into a Home.** The join link and everything built only for it are removed.

1. **Admin: Members → Invite** creates a link at once and shows it as a QR code with a copy button (and the platform's
   share sheet where there is one). There is no choice to make and no screen to paste into.
2. **Link options:** none at creation. A link is single-use and lasts 7 days ([[ADR-018]]'s defaults). A smaller
   "Manage links" entry on the same screen lists the active links and revokes them.
3. **Joiner: the picker's Join a home** goes straight to a screen to paste or scan an invite link, then back to the
   picker, where a card shows the request waiting ([[ADR-018]]). The joiner does not show a QR code.
4. **Approval is always required.** It is the only check on a link that has leaked or been forwarded, and costs one tap.
   Requests stay on the Members screen.
5. **Badge only.** The Members tab of the bottom bar (there is no separate Members tile on the dashboard) shows a badge with the number of
   requests waiting for the user, if they are an admin. No background notification yet; that depends on mobile
   background behaviour, which is undesigned.
6. **Opening a link from a message** (an Android and desktop `vesta://invite/…` handler that lands on the paste screen with
   the link filled in) is wanted but is a separate follow-up and not part of the slices below.

## Removed

`JoinLink`, `HomeJoining.openLink`, `cancelLink` and `invite`, `InviteFailure` and `InviteException`, `JoinHomeFlow`'s
QR screen, `HomeInviteScreen` and `HomeInviteViewModel` (the paste-a-join-link screen), the `offered` link state and its
branch in `HomeJoinService.acceptInvite`, and their tests. `HomeJoinMessage.Invite` and `Accepted` stay: the admin still
sends an Invite after approval, and the joiner now accepts one only from an agent that its invite link named.
`HomeJoining.joined` stays only if something still needs it.

## Consequences

- Joining never needs both devices online at once, and there is one flow to explain.
- A person cannot ask an admin to invite them by showing a code. They must be sent a link.
- A forwarded link only ever produces a request an admin can decline; the admin sees the requester's name.
- Existing short-lived join links stop working. Nothing has shipped with them yet.
- The picker no longer opens a Home by itself on joining; the Home appears in the list once approved. Whether to open it
  automatically is a later UX choice.

## Implementation notes

- **Slice 1** removed the join-link code and rewired Members → Invite and Join a home to the invite screens.
- **Slice 2:** opening Invite makes a link at once (QR and copy) with a "Manage links" button under it; managing lists and
  revokes, and going back after a revoke makes a fresh link in case the one shown was revoked. Each visit makes a new link,
  since a secret can't be shown again once made; unused ones can be revoked. There is **no share sheet yet**: the codebase has
  no share capability, so it needs a platform implementation per target (follow-up). Creating a link writes the creator's
  member entry and this device into the roster first, as a Home made a moment ago hasn't published them yet.
- **Slice 3** was in effect done by slice 1 (the Join a home tile opens the paste or scan screen). It added an app-level test
  of tile → screen → request sent → back at the picker, and reworded the screen to "Join a home" with the tile's text
  pointing at an invite link from an admin.
- **Slice 4:** the badge counts requests still `pending`, not ones already approved, and shows only to admins. It is on the
  Members tab of the bottom bar, which appears on the dashboard and the Members screen, with an accessibility description
  ("N waiting to join").

## Implementation slices

1. Remove the join-link mechanism from the service, interface, flow and tests; accept an Invite only through an invite link.
2. Admin screen: Invite opens the link screen directly (QR, copy, share); "Manage links" lists and revokes.
3. Joiner screen: Join a home opens the paste or scan screen directly.
4. Badge on the Members tab and the dashboard's Members tile.
5. Follow-up: `vesta://invite/…` handlers on Android and desktop.
