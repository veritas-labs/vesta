package uk.veritaslabs.vesta.ui.screens

/** Stable hooks for UI tests to target the Marketplace area screens (ADR-039), independent of displayed copy. */
internal object MarketplaceAreaTestTags {
    const val BackButton = "area_back_button"
    const val Loading = "area_loading"

    const val Disclosure = "area_disclosure"
    const val DisclosureContinue = "area_disclosure_continue"
    const val DisclosureNotNow = "area_disclosure_not_now"

    const val UnavailableUnconfigured = "area_unavailable_unconfigured"
    const val UnavailableNotOnDevice = "area_unavailable_not_on_device"
    const val UnavailableUnreachable = "area_unavailable_unreachable"
    const val TryAgain = "area_try_again"

    const val CountryContinue = "area_country_continue"
    const val CountryMoreComing = "area_country_more_coming"

    fun country(code: String) = "area_country_$code"

    const val HomeSearchField = "area_home_search"
    const val HomeHint = "area_home_hint"
    const val HomeNoMatch = "area_home_no_match"
    const val HomeKeep = "area_home_keep"

    fun place(id: String) = "area_place_$id"

    const val NearbyHome = "area_nearby_home"
    const val NearbyList = "area_nearby_list"
    const val NearbySearchField = "area_nearby_search"
    const val NearbyShowMore = "area_nearby_show_more"
    const val NearbyNoMatch = "area_nearby_no_match"
    const val NearbyTicked = "area_nearby_ticked"
    const val NearbySave = "area_nearby_save"

    fun nearby(id: String) = "area_nearby_$id"

    const val SavedSummary = "area_saved_summary"
    const val SavedEmptyState = "area_saved_empty_state"
    const val SavedChange = "area_saved_change"
    const val SavedForget = "area_saved_forget"

    const val RechooseChoose = "area_rechoose_choose"
}
