package uk.veritaslabs.kits.spaces

/**
 * Told by [ProfileRepository] when the user changes their own Profile, so the change reaches their other devices and
 * every Space they are in (docs/adr/ADR-016-profile-setup.md). Not called for changes that arrive from another device.
 */
interface ProfileChangeListener {
    /** The current Profile's name or avatar was changed here. */
    suspend fun changed()
}
