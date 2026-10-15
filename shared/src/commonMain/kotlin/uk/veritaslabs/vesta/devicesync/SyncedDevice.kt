package uk.veritaslabs.vesta.devicesync

/** A device belonging to this user that their Homes sync to; unrelated to Home members. */
data class SyncedDevice(val name: String, val agentId: String)
