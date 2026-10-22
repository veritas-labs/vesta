package uk.veritaslabs.vesta.ui.screens

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import uk.veritaslabs.vesta.location.BusinessRow
import uk.veritaslabs.vesta.location.foldForSearch

/** How many Pantry items and shopping entries link a merchant key; lets the UI warn before a favourite that is in use is removed. */
fun interface MerchantUsage {
    fun usageCount(merchantKey: String): Int

    companion object {
        val None = MerchantUsage { 0 }

        /** Counts [pantryItems] and [shoppingItems] whose merchant keys include the key (ADR-026). */
        fun of(pantryItems: List<PantryListItem>, shoppingItems: List<ShoppingListItem>) = MerchantUsage { key ->
            pantryItems.count { key in it.merchantKeys } + withMerchantKeys(shoppingItems, pantryItems).count { key in it.merchantKeys }
        }
    }
}

/**
 * My Shops (ADR-039): favouriting a business writes a [Merchant] to the Home's merchants document, keyed by the place id
 * in [Merchant.gersId], so Pantry and Shopping links by merchant key keep working. UI-free.
 */
class FavouriteBusinesses(
    private val source: MerchantsSource,
    private val usage: MerchantUsage = MerchantUsage.None,
) {
    /** The place ids of every favourite, including merchants made by hand that carry a GERS id. */
    val favouriteIds: Flow<Set<String>> = source.merchants.map { list -> list.map { it.gersId }.filter { it.isNotEmpty() }.toSet() }

    /** Every saved merchant, A to Z (see [myShops]). */
    val myShops: Flow<List<Merchant>> = source.merchants.map(::myShops)

    /** Saves [business] as a merchant unless one with its id already exists; returns that merchant's key either way. */
    suspend fun favourite(business: BusinessRow, localityName: String): String {
        existing(business.id)?.let { return it.key }
        return source.save(toMerchant(business, localityName))
    }

    /** Removes the merchant with place id [gersId]; nothing happens if there is none. */
    suspend fun unfavourite(gersId: String) {
        existing(gersId)?.let { source.remove(it.key) }
    }

    /** Pantry items and shopping entries linking the merchant with place id [gersId]; 0 if it is not a favourite. */
    suspend fun usageCount(gersId: String): Int = existing(gersId)?.let { usage.usageCount(it.key) } ?: 0

    /** Removes the merchant with [key], hand-made ones included (they have no place id). */
    suspend fun removeByKey(key: String) = source.remove(key)

    /** Pantry items and shopping entries linking the merchant with [key]. */
    fun usageOfKey(key: String): Int = usage.usageCount(key)

    private suspend fun existing(gersId: String): Merchant? =
        gersId.takeIf { it.isNotEmpty() }?.let { id -> source.merchants.first().firstOrNull { it.gersId == id } }

    companion object {
        fun toMerchant(business: BusinessRow, localityName: String) = Merchant(
            name = business.name,
            type = MerchantType.IndependentTrader,
            customServices = listOf(business.categoryName).filter { it.isNotBlank() },
            latitude = business.latitude,
            longitude = business.longitude,
            address = localityName,
            gersId = business.id,
        )

        /** [merchants] A to Z ignoring case and accents, ties by name then key. */
        fun myShops(merchants: List<Merchant>): List<Merchant> =
            merchants.sortedWith(compareBy<Merchant>({ foldForSearch(it.name) }, { it.name }, { it.key }))
    }
}
