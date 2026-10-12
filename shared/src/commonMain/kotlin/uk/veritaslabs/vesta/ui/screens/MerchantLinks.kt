package uk.veritaslabs.vesta.ui.screens

/**
 * A change to the ordered merchant keys of Pantry items (ADR-026, "Linking to the Pantry"): the first key of an item
 * is the merchant to buy it from first.
 */
sealed interface MerchantLinkChange {

    /** The keys that result from applying this change to an item's [current] ones. */
    fun applyTo(current: List<String>): List<String>

    /** Appends [keys] at the lowest priority, skipping any the item already has. */
    data class Add(val keys: List<String>) : MerchantLinkChange {
        override fun applyTo(current: List<String>) = normalizeMerchantKeys(current + keys)
    }

    /** Takes [keys] off the item, leaving the rest in order. */
    data class Remove(val keys: Set<String>) : MerchantLinkChange {
        override fun applyTo(current: List<String>) = normalizeMerchantKeys(current.filter { it !in keys })
    }

    /** Gives the item exactly [keys], in that order. */
    data class Replace(val keys: List<String>) : MerchantLinkChange {
        override fun applyTo(current: List<String>) = normalizeMerchantKeys(keys)
    }
}

/** [keys] without blank or repeated entries; a key's first position wins. */
fun normalizeMerchantKeys(keys: List<String>): List<String> = keys.filter { it.isNotBlank() }.distinct()

/**
 * The merchants [keys] name, in the same order, skipping keys no merchant has (a deleted merchant's link stays in the
 * document but is not shown).
 */
fun linkedMerchants(keys: List<String>, merchants: List<Merchant>): List<Merchant> {
    val byKey = merchants.associateBy { it.key }
    return keys.mapNotNull { byKey[it] }
}

/**
 * [keys] with [key] moved one place towards the front ([up]) or back, past the next key for which [isVisible] holds, so
 * that keys the user can't see (deleted merchants) don't make a move look like nothing happened. Unchanged if [key]
 * can't move that way.
 */
fun moveMerchantKey(keys: List<String>, key: String, up: Boolean, isVisible: (String) -> Boolean): List<String> {
    val from = keys.indexOf(key)
    if (from < 0) return keys
    val range = if (up) (from - 1 downTo 0) else (from + 1 until keys.size)
    val to = range.firstOrNull { isVisible(keys[it]) } ?: return keys
    return keys.toMutableList().also { it[from] = keys[to]; it[to] = key }
}
