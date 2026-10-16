package uk.veritaslabs.kits.sync

/** A device belonging to this user that their Spaces sync to; unrelated to Space members. */
data class SyncedDevice(val name: String, val agentId: String)
