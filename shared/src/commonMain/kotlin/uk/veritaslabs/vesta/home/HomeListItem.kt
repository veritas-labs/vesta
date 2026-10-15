package uk.veritaslabs.vesta.home

import org.jetbrains.compose.resources.DrawableResource

/** A single row of the home picker screen's home list — see [HomePickerScreen]. */
data class HomeListItem(
    val id: String,
    val name: String,
    val avatar: DrawableResource? = null,
)
