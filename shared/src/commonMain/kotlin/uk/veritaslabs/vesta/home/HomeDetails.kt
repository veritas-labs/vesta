package uk.veritaslabs.vesta.home

/**
 * The editable fields of a Home; [shape] is its stored value (`Home.shape`). [id] is null while creating a new Home — the
 * caller assigns one on save — and set to the existing Home's id while editing.
 */
data class HomeDetails(
    val id: String? = null,
    val name: String = "",
    val shape: String? = null,
)
