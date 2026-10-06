package uk.veritaslabs.vesta.homegroup

/** A scriptable [HomeHub] for view-model tests. */
class FakeHomeHub : HomeHub {
    var enrolment: HubEnrolment? = HubEnrolment("group-1", "x0x group join invite:group-1\nx0x group store create group-1 mailbox")
    var hub: String? = null
    var inGroup = false
    var candidates: List<String> = emptyList()
    var useResult = HubResult.Done
    var removeResult = HubResult.Done
    var failing = false

    var enrolmentCalls = 0
    val used = mutableListOf<String>()
    var removeCalls = 0

    override suspend fun enrolment(homeId: String): HubEnrolment? {
        enrolmentCalls++
        return enrolment
    }

    override suspend fun state(homeId: String): HubState {
        if (failing) error("x0x is down")
        return HubState(hub, inGroup)
    }

    override suspend fun candidates(homeId: String): List<String> = candidates

    override suspend fun useAsHub(homeId: String, agentId: String): HubResult {
        used += agentId
        if (useResult == HubResult.Done) {
            hub = agentId
            inGroup = true
            candidates = candidates - agentId
        }
        return useResult
    }

    override suspend fun removeHub(homeId: String): HubResult {
        removeCalls++
        if (removeResult == HubResult.Done) {
            hub = null
            inGroup = false
        }
        return removeResult
    }
}
