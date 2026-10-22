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
    const val SavedSeeBusinesses = "area_saved_see_businesses"

    const val RechooseChoose = "area_rechoose_choose"

    const val BusinessesSummary = "area_businesses_summary"
    const val BusinessesTotal = "area_businesses_total"
    const val BusinessesInside = "area_businesses_inside"
    const val BusinessesNearby = "area_businesses_nearby"
    const val BusinessesBoth = "area_businesses_both"
    const val BusinessesToggleNearby = "area_businesses_toggle_nearby"
    const val BusinessesNone = "area_businesses_none"
    const val BusinessesDownload = "area_businesses_download"
    const val BusinessesConfirmDialog = "area_businesses_confirm_dialog"
    const val BusinessesConfirmDownload = "area_businesses_confirm_download"
    const val BusinessesConfirmNotNow = "area_businesses_confirm_not_now"
    const val BusinessesLoading = "area_businesses_loading"
    const val BusinessesProgress = "area_businesses_progress"
    const val BusinessesCancel = "area_businesses_cancel"
    const val BusinessesUnreachable = "area_businesses_unreachable"
    const val BusinessesTryAgain = "area_businesses_try_again"
    const val BusinessesList = "area_businesses_list"
    const val BusinessesHeader = "area_businesses_header"
    const val BusinessesSearchField = "area_businesses_search"
    const val BusinessesGroups = "area_businesses_groups"
    const val BusinessesBrowseSearch = "area_businesses_browse_search"
    const val BusinessesSearchAll = "area_businesses_search_all"
    const val BusinessesAll = "area_businesses_all"
    const val BusinessesCategories = "area_businesses_categories"
    const val BusinessesCategorySearch = "area_businesses_category_search"
    const val BusinessesCategoriesNoMatch = "area_businesses_categories_no_match"
    const val BusinessesAllInGroup = "area_businesses_all_in_group"
    const val BusinessesListTitle = "area_businesses_list_title"
    const val BusinessesPartial = "area_businesses_partial"
    const val BusinessesPartialRetry = "area_businesses_partial_retry"
    const val BusinessesShowMore = "area_businesses_show_more"
    const val BusinessesNoMatch = "area_businesses_no_match"
    const val MissingNote = "area_missing_note"
    const val MissingOpen = "area_missing_open"

    const val MyShopsRow = "area_my_shops_row"
    const val MyShopsList = "area_my_shops_list"
    const val MyShopsEmpty = "area_my_shops_empty"
    const val RemoveShopDialog = "area_remove_shop_dialog"
    const val RemoveShopConfirm = "area_remove_shop_confirm"
    const val RemoveShopCancel = "area_remove_shop_cancel"

    fun favouriteHeart(placeId: String) = "area_favourite_heart_$placeId"

    fun myShopsItem(key: String) = "area_my_shops_item_$key"

    fun myShopsHeart(key: String) = "area_my_shops_heart_$key"

    fun businessesGroup(key: String) = "area_businesses_group_$key"

    fun businessesCategory(index: Int) = "area_businesses_category_$index"

    fun businessesSection(localityRow: Int) = "area_businesses_section_$localityRow"

    fun businessesRow(id: String) = "area_businesses_row_$id"
}
