# Device pairing = "link a new device to an existing identity" from
# docs/adr/ADR-003-device-sync.md, section "Device enrollment ('link a new
# device')" onward. ADR-003's Status is "Proposed" and explicitly flags every
# code sketch as illustrative, not final — these scenarios are written
# against the illustrative production API in
# shared/src/jvmMain/.../devicepairing/DevicePairing.kt, which throws
# NotImplementedError for every operation. They are meant to fail (red) until
# that mechanism is actually built; that is the point, not a bug in this file.
#
# UI/copy is intentionally NOT specified here. ADR-003 itself flags the
# onboarding entry point for this flow (illustratively "S1c") as undesigned —
# "a product/design decision belonging with the rest of
# docs/design/onboarding.md, not this document" — and onboarding.md has no
# such screen today. Scenarios below stay at the mechanism level (x0x calls,
# certificates, group membership) rather than inventing screen text.
#
# Sync mechanics once a device is enrolled (broadcast, catch-up, cold-start)
# are explicitly "unchanged from ADR-002" per ADR-003 — that's the device-sync
# feature, not device pairing, and belongs in its own feature file.

Feature: Device pairing (linking a new device to an existing identity)

  Vesta has no account system. A user's own device fleet has no bootstrap
  today comparable to a Home's invite code — device pairing is that
  bootstrap: proximity-based, device-to-device, the same shape as
  WhatsApp/Signal "link a device."

  Background:
    Given a user already has a Profile on an existing device, "the owner device"
    And the owner device holds the only copy of that user's "~/.x0x/user.key"

  Scenario: New device generates its own agent keypair and renders a pairing QR code
    Given a brand new device with Vesta freshly installed and its x0x daemon started
    When the new device begins the "link an existing device" flow
    Then the new device generates its own local ML-DSA-65 agent keypair
    And the new device's private key never leaves that device
    And the new device renders a QR code encoding its agent public key

  Scenario: Owner device scans the new device's QR and issues a rider certificate
    Given the new device is displaying its pairing QR code
    When the owner device scans that QR code
    Then the owner device calls "owner agents issue" with the scanned public key and mode "rider"
    And a signed AgentCertificate binding the new device's agent id to the shared user id is produced

  Scenario: Owner device invites the newly certified device into the DeviceSync group
    Given the owner device has issued a rider AgentCertificate for the new device
    When the owner device creates or reuses this user's "DeviceSync" x0x group
    Then the owner device requests a group invite for that group
    And the owner device sends the AgentCertificate and the invite to the new device's agent id by direct message

  Scenario: New device redeems its invite and becomes an active group member
    Given the new device has received its AgentCertificate and a "DeviceSync" group invite by direct message
    When the new device redeems the invite
    Then the new device polls group membership until its own agent shows state "active"

  Scenario: End-to-end pairing of a user's second device
    Given a user has only the owner device and no other linked devices
    When the user completes the "link an existing device" flow from a second, brand new device
    Then the second device becomes an active member of the user's "DeviceSync" group
    And a peer watermark row is created for the new device's agent id, starting at applied version 0

  Scenario: Pairing a third device onto an already-multi-device identity
    Given a user's owner device and one already-linked rider device are both active "DeviceSync" group members
    When the user completes the "link an existing device" flow from a third, brand new device
    Then the third device becomes an active member of the same "DeviceSync" group
    And the group now has three active members

  @hardening
  Scenario: Owner device pins the rider's machine id after admission
    Given a new rider device has just been admitted as an active "DeviceSync" group member
    When the owner device receives any direct message from that rider's agent id
    Then the owner device learns the rider's machine id from that message
    And the owner device pins the rider's agent id and machine id together

  @hardening
  Scenario: A pinned rider observed from an unexpected machine is flagged
    Given a rider's agent id and machine id are already pinned together
    When a message purporting to be from that rider's agent id arrives from a different machine id
    Then trust evaluation flags or rejects the connection
    # A still-valid agent key is not by itself sufficient to accept the
    # connection once a pin exists — see ADR-003 "Post-enrollment hardening."

  Scenario: Revoking a linked device
    Given a rider device is an active "DeviceSync" group member with a pinned machine id
    When the user revokes that device
    Then the rider's x0x identity is revoked
    And the rider is removed from the "DeviceSync" group, rotating its MLS epoch
    And the rider's pinned agent id and machine id pin is removed

  @known-limitation
  Scenario: Revocation does not remove data already replicated to a lost device
    Given a rider device has synced private data before being lost or stolen
    When the user revokes that device after the fact
    Then future sync to that device stops
    But data already replicated onto that device before revocation is not deleted or made inaccessible
    # Local-first limitation, not a bug to fix — see ADR-003 Consequences §4.
    # Flagged to product: the "lost phone" story is "your data is safe going
    # forward," not "your data is deleted from the phone you lost."

  @known-limitation
  Scenario: Copying ~/.x0x/user.key between devices instead of pairing is not supported
    Given two devices independently hold a copy of the same raw "~/.x0x/user.key"
    When each device's x0x daemon starts
    Then x0x provisions two distinct x0x "Homes" for what was meant to be one identity
    # Documented as actively broken today (x0x issue #449), not a slower or
    # less-safe alternative to the pairing flow above — see ADR-003 "x0x's own
    # Human identity layer is the trust anchor."

  @open-question @wip
  Scenario: Recovering when the owner device is lost before any rider was ever linked
    Given a user's identity has exactly one device holding "~/.x0x/user.key"
    And no rider device has ever been linked
    When that sole owner device is lost
    Then there is currently no documented way to certify a replacement device
    # ADR-003 Open Questions calls this "the single biggest unresolved risk in
    # this design, not a hypothetical." Intentionally left unresolved here —
    # not a gap in this feature file, a gap in the design it specs.

  @open-question @wip
  Scenario: Cold-start bootstrap for a freshly linked device's existing private data
    Given a device has just become an active "DeviceSync" group member
    And that user's other devices already hold private-table data
    When the new device joins
    Then the new device has zero local rows for every private table
    # Full snapshot transfer for a first-time joiner/linked device is an open
    # question ADR-003 shares with ADR-002; not resolved here.
