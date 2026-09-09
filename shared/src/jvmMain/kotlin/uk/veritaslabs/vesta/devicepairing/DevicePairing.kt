package uk.veritaslabs.vesta.devicepairing

/**
 * Illustrative production surface for ADR-003's "Device enrollment ('link a
 * new device')" mechanism (docs/adr/ADR-003-device-sync.md). ADR-003's
 * Status is Proposed and none of this is implemented — every function below
 * exists only so
 * shared/src/jvmTest/kotlin/uk/veritaslabs/vesta/devicepairing/DevicePairingSteps.kt
 * has real symbols to compile against; each throws until a real
 * implementation lands. Failing (red) Cucumber scenarios against this file
 * are the intended state, not a bug — see
 * shared/src/jvmTest/resources/features/device-pairing.feature.
 *
 * JVM-only for now: this isn't wired into the shared X0xClient interface
 * (commonMain, implemented per-platform) because there is nothing concrete
 * yet for Android/iOS actuals to implement, and adding stub overrides there
 * purely to keep other platforms compiling would misrepresent how settled
 * this mechanism is.
 */

data class AgentKeypair(val agentId: String, val publicKeyHex: String)

data class PairingQrPayload(val publicKeyHex: String)

data class AgentCertificate(val agentId: String, val userId: String, val mode: String)

data class GroupInvite(val groupId: String, val inviteToken: String)

data class MachinePin(val agentId: String, val machineId: String)

sealed interface TrustDecision {
    data object Accepted : TrustDecision
    data class Flagged(val reason: String) : TrustDecision
}

object DevicePairing {

    /** ADR-003 enrollment step 1: new device's own ML-DSA-65 agent keypair. */
    fun generateAgentKeypair(): AgentKeypair =
        TODO("ADR-003 device enrollment step 1 (agent keypair generation) is not implemented")

    /** ADR-003 enrollment step 1: QR encoding of the new device's public key only. */
    fun renderPairingQr(keypair: AgentKeypair): PairingQrPayload =
        TODO("ADR-003 device enrollment step 1 (pairing QR render) is not implemented")

    /** ADR-003 enrollment step 2: `owner agents issue --mode rider` / `POST /owner/agents/issue`. */
    fun issueRiderCertificate(scannedPublicKeyHex: String, label: String): AgentCertificate =
        TODO("ADR-003 device enrollment step 2 (rider certificate issuance) is not implemented")

    /** ADR-003 enrollment step 3: `POST /groups` (create-or-reuse) + `POST /groups/<gid>/invite`. */
    fun createOrReuseDeviceSyncGroupInvite(userId: String): GroupInvite =
        TODO("ADR-003 device enrollment step 3 (DeviceSync group invite) is not implemented")

    /** ADR-003 enrollment step 3: DM the certificate + invite to the new device's agent id. */
    fun sendCertificateAndInvite(toAgentId: String, certificate: AgentCertificate, invite: GroupInvite) {
        TODO("ADR-003 device enrollment step 3 (certificate+invite delivery) is not implemented")
    }

    /** ADR-003 enrollment step 4: `POST /groups/join`. */
    fun redeemInvite(invite: GroupInvite) {
        TODO("ADR-003 device enrollment step 4 (invite redemption) is not implemented")
    }

    /** ADR-003 enrollment step 4: poll `GET /groups/<gid>/members` until `state: "active"`. */
    fun pollUntilActiveMember(groupId: String, agentId: String): Boolean =
        TODO("ADR-003 device enrollment step 4 (membership activation poll) is not implemented")

    /** The `DeviceSyncPeer.lastAppliedDbVersion` a newly linked peer starts at, per ADR-003's table sketch. */
    fun initialPeerWatermark(agentId: String): Long =
        TODO("ADR-003 DeviceSyncPeer watermark row creation is not implemented")

    fun activeMemberCount(groupId: String): Int =
        TODO("ADR-003 DeviceSync group membership query is not implemented")

    /** Every inbound DM carries `machine_id` for free — ADR-003 "Post-enrollment hardening." */
    fun learnMachineIdFromDirectMessage(agentId: String): String =
        TODO("ADR-003 post-enrollment hardening (machine id observation) is not implemented")

    /** `POST /contacts/<agent_id>/machines/<machine_id>/pin`. */
    fun pinMachineId(agentId: String, machineId: String): MachinePin =
        TODO("ADR-003 post-enrollment hardening (machine id pinning) is not implemented")

    /** `POST /trust/evaluate` (or the equivalent gate on inbound gossip/DM). */
    fun evaluateTrust(agentId: String, observedMachineId: String): TrustDecision =
        TODO("ADR-003 post-enrollment hardening (trust evaluation) is not implemented")

    /** ADR-003 Consequences §4: `x0x identity revoke` + group removal (MLS epoch rotation) + unpin. */
    fun revokeDevice(agentId: String) {
        TODO("ADR-003 revocation is not implemented")
    }

    fun isActiveGroupMember(groupId: String, agentId: String): Boolean =
        TODO("ADR-003 DeviceSync group membership query is not implemented")

    fun isPinned(agentId: String): Boolean =
        TODO("ADR-003 post-enrollment hardening (pin lookup) is not implemented")
}
