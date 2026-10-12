package uk.veritaslabs.vesta.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val StopTimeoutMillis = 5_000L
private const val MinutesPerDay = 24 * 60

/** What sort of seller a [Merchant] is (ADR-026). Stored by [name]. */
enum class MerchantType { MarketTrader, IndependentTrader, LocalChain, NationalChain, OnlineRetailer, Other }

/** A service a [Merchant] offers that the app knows by name, so it is labelled and translated. Stored by [name]. */
enum class MerchantService { Groceries, Butcher, Greengrocer, Fishmonger, Bakery, Deli, Cafe, Pharmacy, Household }

/** The days of the week, Monday first. */
enum class Weekday { Monday, Tuesday, Wednesday, Thursday, Friday, Saturday, Sunday }

/** One stretch of a day a merchant is open, in minutes after midnight: [openMinute] up to [closeMinute], at most 24:00 (1440). */
data class OpenRange(val openMinute: Int, val closeMinute: Int) {
    val isValid: Boolean get() = openMinute in 0 until MinutesPerDay && closeMinute in 1..MinutesPerDay && openMinute < closeMinute
}

/**
 * When a merchant is open: the [OpenRange]s of each [Weekday] (none means closed that day), and a free-text [note] such as
 * "market days only". Ranges are kept sorted and valid; see [normalised].
 */
data class OpeningHours(
    val days: Map<Weekday, List<OpenRange>> = emptyMap(),
    val note: String = "",
) {
    /** The ranges for [day], empty when closed. */
    fun on(day: Weekday): List<OpenRange> = days[day].orEmpty()

    val isEmpty: Boolean get() = days.values.all { it.isEmpty() } && note.isBlank()

    /** These hours with invalid ranges dropped, each day's ranges sorted, and the note trimmed. */
    fun normalised() = OpeningHours(
        days = Weekday.entries.associateWith { day -> on(day).filter { it.isValid }.sortedBy { it.openMinute } }.filterValues { it.isNotEmpty() },
        note = note.trim(),
    )
}

/** A seller the Home buys from, anything from a market stall to a supermarket (ADR-026); a blank [key] is one that hasn't been saved yet. */
data class Merchant(
    val key: String = "",
    val name: String = "",
    val type: MerchantType = MerchantType.IndependentTrader,
    val services: List<MerchantService> = emptyList(),
    /** Services the user typed in that [MerchantService] doesn't name. */
    val customServices: List<String> = emptyList(),
    val hours: OpeningHours = OpeningHours(),
    /** Degrees, both set or both null. */
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** Where to find it in words: an address, or "stall 14, north aisle". */
    val address: String = "",
    /** The Overture Maps GERS id of the place, if known. */
    val gersId: String = "",
    /** The merchant's x0x agent id, if it has one. */
    val x0xAgentId: String = "",
) {
    val hasLocation: Boolean get() = latitude != null && longitude != null
}

/** Where [MerchantsViewModel] gets a Home's merchants from; implemented over the merchants Automerge document (ADR-026). */
interface MerchantsSource {
    val merchants: Flow<List<Merchant>>

    /** Adds [merchant] (blank key) or replaces the merchant at its key; returns the key. */
    suspend fun save(merchant: Merchant): String

    suspend fun remove(key: String)
}

/** [Merchant.latitude] and [Merchant.longitude] are stored as whole millionths of a degree so the document needs no floating point. */
const val MicrodegreesPerDegree = 1_000_000

fun degreesToMicrodegrees(degrees: Double): Int = (degrees * MicrodegreesPerDegree).roundToLong().toInt()

fun microdegreesToDegrees(micro: Int): Double = micro.toDouble() / MicrodegreesPerDegree

/** Minutes after midnight as "HH:MM" (1440 is "24:00"). */
fun formatTime(minutes: Int): String = "${(minutes / 60).toString().padStart(2, '0')}:${(minutes % 60).toString().padStart(2, '0')}"

/** "H:MM", "HH:MM" or "HHMM" as minutes after midnight (up to "24:00"), or null if it isn't a time. */
fun parseTime(text: String): Int? {
    val trimmed = text.trim()
    val (hours, minutes) = when {
        ':' in trimmed -> trimmed.split(':').takeIf { it.size == 2 }?.let { it[0] to it[1] } ?: return null
        trimmed.length in 3..4 && trimmed.all(Char::isDigit) -> trimmed.dropLast(2) to trimmed.takeLast(2)
        else -> return null
    }
    if (hours.length !in 1..2 || minutes.length != 2 || !hours.all(Char::isDigit) || !minutes.all(Char::isDigit)) return null
    val total = hours.toInt() * 60 + minutes.toInt()
    return total.takeIf { minutes.toInt() < 60 && it <= MinutesPerDay }
}

/** A coordinate typed as text: a number within -[limit]..[limit], or null. Accepts a comma as the decimal separator. */
fun parseCoordinate(text: String, limit: Double): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= -limit && it <= limit }

/** Predefined [services] first, then [custom] tags, as one list of names the way the document stores them. */
fun serviceNames(services: List<MerchantService>, custom: List<String>): List<String> = services.map { it.name } + custom

/**
 * Splits stored service [names] into the [MerchantService]s they name and the rest. A typed tag that equals a predefined
 * service's name (ignoring case) counts as that service, and tags repeated ignoring case are kept once.
 */
fun splitServices(names: List<String>): Pair<List<MerchantService>, List<String>> {
    val known = mutableListOf<MerchantService>()
    val custom = mutableListOf<String>()
    names.map { it.trim() }.filter { it.isNotEmpty() }.forEach { name ->
        val service = MerchantService.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
        when {
            service != null -> if (service !in known) known += service
            custom.none { it.equals(name, ignoreCase = true) } -> custom += name
        }
    }
    return known to custom
}

/** What the user has chosen to narrow the merchants to; the empty filter shows everything. */
data class MerchantsFilter(
    val query: String = "",
    val type: MerchantType? = null,
) {
    val isActive: Boolean get() = query.isNotBlank() || type != null
}

/** The [merchants] matching [filter]; the query matches the name, address or a service (by name or custom tag), ignoring case. */
fun filterMerchants(merchants: List<Merchant>, filter: MerchantsFilter): List<Merchant> {
    val query = filter.query.trim().lowercase()
    return merchants.filter { merchant ->
        (filter.type == null || merchant.type == filter.type) &&
            (
                query.isEmpty() ||
                    merchant.name.lowercase().contains(query) ||
                    merchant.address.lowercase().contains(query) ||
                    serviceNames(merchant.services, merchant.customServices).any { it.lowercase().contains(query) }
                )
    }
}

/** [MerchantsScreen]'s state; [isLoading] is true until the source has emitted for the first time. */
data class MerchantsUiState(
    /** Every merchant, sorted by name. */
    val merchants: List<Merchant> = emptyList(),
    val filter: MerchantsFilter = MerchantsFilter(),
    val isLoading: Boolean = true,
) {
    /** The [merchants] the [filter] lets through. */
    val visible: List<Merchant> get() = filterMerchants(merchants, filter)
}

/** Backs the merchants screens: exposes the merchants from [source] and the user's filter. */
class MerchantsViewModel(private val source: MerchantsSource) : ViewModel() {

    private val filter = MutableStateFlow(MerchantsFilter())

    val uiState: StateFlow<MerchantsUiState> = combine(source.merchants, filter) { merchants, current ->
        MerchantsUiState(merchants = merchants.sortedBy { it.name.lowercase() }, filter = current, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), MerchantsUiState())

    fun setQuery(query: String) = filter.update { it.copy(query = query) }

    /** Filters to [type], or clears the type filter if it is already [type]. */
    fun toggleType(type: MerchantType) = filter.update { it.copy(type = if (it.type == type) null else type) }

    fun save(merchant: Merchant) {
        viewModelScope.launch { source.save(merchant) }
    }

    fun remove(key: String) {
        viewModelScope.launch { source.remove(key) }
    }
}
