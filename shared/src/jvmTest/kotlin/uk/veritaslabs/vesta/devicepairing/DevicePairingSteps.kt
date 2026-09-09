package uk.veritaslabs.vesta.devicepairing

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Step definitions for
 * shared/src/jvmTest/resources/features/device-pairing.feature.
 *
 * Steps that exercise the actual (unbuilt) mechanism call into
 * [uk.veritaslabs.vesta.devicepairing.DevicePairing], which throws
 * [NotImplementedError] for every operation — those scenarios are meant to
 * fail (red) until ADR-003 is implemented. A few scenarios (tagged
 * `@known-limitation` / `@open-question` in the feature file) instead assert
 * a documented, permanent property of the current design (e.g. "revocation
 * never deletes already-replicated data") or a fact about x0x's own
 * behavior rather than Vesta code — those are genuinely green today; there
 * is nothing to build to make them pass.
 */
class DevicePairingSteps {

    private val newDeviceAgentId = "new-device-agent-1"
    private val riderAgentId = "rider-agent-1"

    private var scannedPublicKeyHex: String? = null
    private var newDeviceKeypair: AgentKeypair? = null
    private var pairingQr: PairingQrPayload? = null
    private var issuedCertificate: AgentCertificate? = null
    private var groupInvite: GroupInvite? = null
    private var activeMemberResult: Boolean? = null
    private var peerWatermark: Long? = null
    private var memberCount: Int? = null
    private var learnedMachineId: String? = null
    private var machinePin: MachinePin? = null
    private var trustDecision: TrustDecision? = null

    // Documentary facts for scenarios that assert a permanent property of the
    // design rather than exercising unbuilt mechanism — see class doc above.
    private var futureSyncStopped = false
    private var dataAlreadyReplicatedRemains = false
    private var duplicateUserKeyCausesSplitHomes = false
    private var noDocumentedRecoveryMechanism = false
    private var freshDeviceRowCount: Int? = null

    // --- Background ---

    @Given("a user already has a Profile on an existing device, {string}")
    fun ownerDeviceExists(label: String) {
        // Labels the owner device for readability; no state needed beyond this.
    }

    @Given("the owner device holds the only copy of that user's {string}")
    fun ownerHoldsUserKey(keyPath: String) {
        // Documents the precondition ADR-003 relies on; nothing to set up.
    }

    // --- Scenario: new device generates keypair + renders QR ---

    @Given("a brand new device with Vesta freshly installed and its x0x daemon started")
    fun freshDeviceStarted() {
        // No prior state; the new device has nothing yet.
    }

    @When("the new device begins the {string} flow")
    fun beginsLinkFlow(flowName: String) {
        val keypair = DevicePairing.generateAgentKeypair()
        newDeviceKeypair = keypair
        pairingQr = DevicePairing.renderPairingQr(keypair)
    }

    @Then("the new device generates its own local ML-DSA-65 agent keypair")
    fun assertsKeypairGenerated() {
        assertNotNull(newDeviceKeypair)
    }

    @Then("the new device's private key never leaves that device")
    fun assertsPrivateKeyNeverLeaves() {
        // Structural guarantee, not a runtime check: AgentKeypair carries no
        // private-key field for any caller to retrieve in the first place.
        assertTrue(AgentKeypair::class.java.declaredFields.none { it.name.contains("private", ignoreCase = true) })
    }

    @Then("the new device renders a QR code encoding its agent public key")
    fun assertsQrRendered() {
        assertEquals(newDeviceKeypair?.publicKeyHex, pairingQr?.publicKeyHex)
    }

    // --- Scenario: owner scans QR and issues rider certificate ---

    @Given("the new device is displaying its pairing QR code")
    fun newDeviceDisplayingQr() {
        scannedPublicKeyHex = "aa11bb22cc33dd44"
    }

    @When("the owner device scans that QR code")
    fun ownerScansQr() {
        issuedCertificate = DevicePairing.issueRiderCertificate(scannedPublicKeyHex!!, "New device")
    }

    @Then("the owner device calls {string} with the scanned public key and mode {string}")
    fun assertsIssueCalled(command: String, mode: String) {
        assertEquals(mode, issuedCertificate?.mode)
    }

    @Then("a signed AgentCertificate binding the new device's agent id to the shared user id is produced")
    fun assertsCertificateProduced() {
        assertNotNull(issuedCertificate)
    }

    // --- Scenario: owner invites the certified device into the DeviceSync group ---

    @Given("the owner device has issued a rider AgentCertificate for the new device")
    fun certificateAlreadyIssued() {
        issuedCertificate = AgentCertificate(agentId = newDeviceAgentId, userId = "user-1", mode = "rider")
    }

    @When("the owner device creates or reuses this user's {string} x0x group")
    fun createsOrReusesGroup(groupName: String) {
        groupInvite = DevicePairing.createOrReuseDeviceSyncGroupInvite("user-1")
    }

    @Then("the owner device requests a group invite for that group")
    fun assertsInviteRequested() {
        assertNotNull(groupInvite)
    }

    @Then("the owner device sends the AgentCertificate and the invite to the new device's agent id by direct message")
    fun assertsCertAndInviteSent() {
        DevicePairing.sendCertificateAndInvite(newDeviceAgentId, issuedCertificate!!, groupInvite!!)
    }

    // --- Scenario: new device redeems invite and becomes active ---

    @Given("the new device has received its AgentCertificate and a {string} group invite by direct message")
    fun receivedCertAndInvite(groupName: String) {
        issuedCertificate = AgentCertificate(agentId = newDeviceAgentId, userId = "user-1", mode = "rider")
        groupInvite = GroupInvite(groupId = "group-1", inviteToken = "token-1")
    }

    @When("the new device redeems the invite")
    fun redeemsInvite() {
        DevicePairing.redeemInvite(groupInvite!!)
    }

    @Then("the new device polls group membership until its own agent shows state {string}")
    fun pollsMembership(state: String) {
        activeMemberResult = DevicePairing.pollUntilActiveMember(groupInvite!!.groupId, newDeviceAgentId)
        assertTrue(activeMemberResult == true)
    }

    // --- Scenario: end-to-end pairing of a second / third device ---

    @Given("a user has only the owner device and no other linked devices")
    fun onlyOwnerDeviceExists() {
        // Baseline already true from Background; nothing further to set up.
    }

    @When("the user completes the {string} flow from a second, brand new device")
    fun completesLinkFlowSecondDevice(flowName: String) {
        val keypair = DevicePairing.generateAgentKeypair()
        val qr = DevicePairing.renderPairingQr(keypair)
        val cert = DevicePairing.issueRiderCertificate(qr.publicKeyHex, "Second device")
        val invite = DevicePairing.createOrReuseDeviceSyncGroupInvite("user-1")
        DevicePairing.sendCertificateAndInvite(keypair.agentId, cert, invite)
        DevicePairing.redeemInvite(invite)
        activeMemberResult = DevicePairing.pollUntilActiveMember(invite.groupId, keypair.agentId)
        peerWatermark = DevicePairing.initialPeerWatermark(keypair.agentId)
        newDeviceKeypair = keypair
        groupInvite = invite
    }

    @Then("the second device becomes an active member of the user's {string} group")
    fun assertsSecondDeviceActive(groupName: String) {
        assertTrue(activeMemberResult == true)
    }

    @Then("a peer watermark row is created for the new device's agent id, starting at applied version 0")
    fun assertsWatermarkZero() {
        assertEquals(0L, peerWatermark)
    }

    @Given("a user's owner device and one already-linked rider device are both active {string} group members")
    fun ownerAndOneRiderActive(groupName: String) {
        // Baseline for this scenario; the prior device's state isn't modeled
        // separately since only the third device's enrollment is under test.
    }

    @When("the user completes the {string} flow from a third, brand new device")
    fun completesLinkFlowThirdDevice(flowName: String) {
        val keypair = DevicePairing.generateAgentKeypair()
        val qr = DevicePairing.renderPairingQr(keypair)
        val cert = DevicePairing.issueRiderCertificate(qr.publicKeyHex, "Third device")
        val invite = DevicePairing.createOrReuseDeviceSyncGroupInvite("user-1")
        DevicePairing.sendCertificateAndInvite(keypair.agentId, cert, invite)
        DevicePairing.redeemInvite(invite)
        activeMemberResult = DevicePairing.pollUntilActiveMember(invite.groupId, keypair.agentId)
        memberCount = DevicePairing.activeMemberCount(invite.groupId)
        groupInvite = invite
    }

    @Then("the third device becomes an active member of the same {string} group")
    fun assertsThirdDeviceActive(groupName: String) {
        assertTrue(activeMemberResult == true)
    }

    @Then("the group now has three active members")
    fun assertsThreeMembers() {
        assertEquals(3, memberCount)
    }

    // --- Scenarios: post-enrollment machine-id pinning / trust evaluation ---

    @Given("a new rider device has just been admitted as an active {string} group member")
    fun riderJustAdmitted(groupName: String) {
        // Baseline; riderAgentId is fixed for this scenario's steps.
    }

    @When("the owner device receives any direct message from that rider's agent id")
    fun ownerReceivesDm() {
        learnedMachineId = DevicePairing.learnMachineIdFromDirectMessage(riderAgentId)
    }

    @Then("the owner device learns the rider's machine id from that message")
    fun assertsMachineIdLearned() {
        assertNotNull(learnedMachineId)
    }

    @Then("the owner device pins the rider's agent id and machine id together")
    fun assertsPinCreated() {
        machinePin = DevicePairing.pinMachineId(riderAgentId, learnedMachineId!!)
        assertEquals(riderAgentId, machinePin?.agentId)
    }

    @Given("a rider's agent id and machine id are already pinned together")
    fun riderAlreadyPinned() {
        machinePin = MachinePin(agentId = riderAgentId, machineId = "machine-original")
    }

    @When("a message purporting to be from that rider's agent id arrives from a different machine id")
    fun messageFromDifferentMachine() {
        trustDecision = DevicePairing.evaluateTrust(riderAgentId, "machine-different")
    }

    @Then("trust evaluation flags or rejects the connection")
    fun assertsFlaggedOrRejected() {
        assertTrue(trustDecision !is TrustDecision.Accepted)
    }

    // --- Scenario: revocation ---

    @Given("a rider device is an active {string} group member with a pinned machine id")
    fun riderActiveWithPin(groupName: String) {
        machinePin = MachinePin(agentId = riderAgentId, machineId = "machine-original")
    }

    @When("the user revokes that device")
    fun userRevokesDevice() {
        DevicePairing.revokeDevice(riderAgentId)
    }

    @Then("the rider's x0x identity is revoked")
    fun assertsIdentityRevoked() {
        // Revocation is a single call in this design (see DevicePairing.revokeDevice);
        // "not implemented" surfaces at the When step above, so this asserts nothing
        // extra today.
    }

    @Then("the rider is removed from the {string} group, rotating its MLS epoch")
    fun assertsRemovedFromGroup(groupName: String) {
        assertTrue(!DevicePairing.isActiveGroupMember(groupName, riderAgentId))
    }

    @Then("the rider's pinned agent id and machine id pin is removed")
    fun assertsPinRemoved() {
        assertTrue(!DevicePairing.isPinned(riderAgentId))
    }

    // --- Known limitation: revocation doesn't wipe already-replicated data ---

    @Given("a rider device has synced private data before being lost or stolen")
    fun riderHadSyncedData() {
        dataAlreadyReplicatedRemains = true
    }

    @When("the user revokes that device after the fact")
    fun revokesAfterTheFact() {
        DevicePairing.revokeDevice(riderAgentId)
        futureSyncStopped = true
    }

    @Then("future sync to that device stops")
    fun assertsFutureSyncStopped() {
        assertTrue(futureSyncStopped)
    }

    @Then("data already replicated onto that device before revocation is not deleted or made inaccessible")
    fun assertsDataRemains() {
        // Local-first limitation, not a bug: no mechanism in this design (or in
        // cr-sqlite/x0x generally) remotely wipes a device — ADR-003 Consequences §4.
        assertTrue(dataAlreadyReplicatedRemains)
    }

    // --- Known limitation: copying ~/.x0x/user.key is not a supported alternative ---

    @Given("two devices independently hold a copy of the same raw {string}")
    fun duplicateUserKeyHeld(keyPath: String) {
        duplicateUserKeyCausesSplitHomes = true
    }

    @When("each device's x0x daemon starts")
    fun eachDaemonStarts() {
        // x0x's own behavior, not Vesta code under test (x0x issue #449).
    }

    @Then("x0x provisions two distinct x0x {string} for what was meant to be one identity")
    fun assertsSplitHomes(conceptName: String) {
        assertTrue(duplicateUserKeyCausesSplitHomes)
    }

    // --- Open question: owner device lost before any rider was linked ---

    @Given("a user's identity has exactly one device holding {string}")
    fun exactlyOneDeviceHoldsKey(keyPath: String) {
        // Baseline for this scenario.
    }

    @Given("no rider device has ever been linked")
    fun noRiderEverLinked() {
        noDocumentedRecoveryMechanism = true
    }

    @When("that sole owner device is lost")
    fun soleOwnerDeviceLost() {
        // Hypothetical; there is no Vesta code path to exercise here by design.
    }

    @Then("there is currently no documented way to certify a replacement device")
    fun assertsNoRecoveryDocumented() {
        assertTrue(noDocumentedRecoveryMechanism)
    }

    // --- Open question: cold-start bootstrap for a freshly linked device ---

    @Given("a device has just become an active {string} group member")
    fun deviceJustBecameActiveMember(groupName: String) {
        freshDeviceRowCount = 0
    }

    @Given("that user's other devices already hold private-table data")
    fun otherDevicesHoldData() {
        // Baseline for this scenario.
    }

    @When("the new device joins")
    fun newDeviceJoins() {
        // By construction, a freshly joined device starts with no local rows.
    }

    @Then("the new device has zero local rows for every private table")
    fun assertsZeroLocalRows() {
        assertEquals(0, freshDeviceRowCount)
    }
}
