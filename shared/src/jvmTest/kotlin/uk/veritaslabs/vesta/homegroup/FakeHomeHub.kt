package uk.veritaslabs.vesta.homegroup

import uk.veritaslabs.kits.spaces.HubEnrolment
import uk.veritaslabs.kits.spaces.HubResult
import uk.veritaslabs.kits.spaces.HubState
import uk.veritaslabs.kits.spaces.SpaceHub

/** A scriptable [SpaceHub] for view-model tests. */
class FakeHomeHub : SpaceHub {
    var enrolment: HubEnrolment? = HubEnrolment("group-1", "x0x group join invite:group-1\nx0x group store create group-1 mailbox")
    var hubIds: List<String> = emptyList()
    var inGroup = false
    var deviceHubs: Set<String> = emptySet()
    var candidates: List<String> = emptyList()
    var ownDevices: List<String> = emptyList()
    var useResult = HubResult.Done
    var removeResult = HubResult.Done
    var failing = false

    var enrolmentCalls = 0
    val used = mutableListOf<String>()
    val removed = mutableListOf<String>()
    val removeCalls: Int get() = removed.size

    override suspend fun enrolment(homeId: String): HubEnrolment? {
        enrolmentCalls++
        return enrolment
    }

    override suspend fun hubs(homeId: String): List<HubState> {
        if (failing) error("x0x is down")
        return hubIds.map { HubState(it, inGroup, isDevice = it in deviceHubs) }
    }

    override suspend fun candidates(homeId: String): List<String> = candidates

    override suspend fun ownDeviceCandidates(homeId: String): List<String> = ownDevices

    override suspend fun useAsHub(homeId: String, agentId: String): HubResult {
        used += agentId
        if (useResult == HubResult.Done) {
            hubIds = hubIds + agentId
            inGroup = true
            candidates = candidates - agentId
            ownDevices = ownDevices - agentId
        }
        return useResult
    }

    override suspend fun removeHub(homeId: String, agentId: String): HubResult {
        removed += agentId
        if (removeResult == HubResult.Done) hubIds = hubIds - agentId
        return removeResult
    }
}
