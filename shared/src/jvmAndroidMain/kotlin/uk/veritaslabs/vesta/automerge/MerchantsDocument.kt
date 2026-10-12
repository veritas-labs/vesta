package uk.veritaslabs.vesta.automerge

import kotlin.jvm.optionals.getOrNull
import org.automerge.AmValue
import org.automerge.Document
import org.automerge.ObjectId
import org.automerge.ObjectType
import org.automerge.Read
import org.automerge.Transaction
import uk.veritaslabs.vesta.ui.screens.Merchant
import uk.veritaslabs.vesta.ui.screens.MerchantType
import uk.veritaslabs.vesta.ui.screens.OpenRange
import uk.veritaslabs.vesta.ui.screens.OpeningHours
import uk.veritaslabs.vesta.ui.screens.Weekday
import uk.veritaslabs.vesta.ui.screens.degreesToMicrodegrees
import uk.veritaslabs.vesta.ui.screens.microdegreesToDegrees
import uk.veritaslabs.vesta.ui.screens.serviceNames
import uk.veritaslabs.vesta.ui.screens.splitServices

private const val IdKey = "id"
private const val NameKey = "name"
private const val TypeKey = "type"
private const val ServicesKey = "services"
private const val HoursKey = "hours"
private const val HoursNoteKey = "hoursNote"
private const val LatitudeKey = "lat"
private const val LongitudeKey = "lon"
private const val AddressKey = "address"
private const val GersIdKey = "gersId"
private const val AgentIdKey = "agentId"

/** Reads every merchant, ordered by name (case-insensitive). */
fun Read.readMerchants(): List<Merchant> {
    return mapEntries(ObjectId.ROOT).getOrNull()
        ?.mapNotNull { (it.value as? AmValue.Map)?.id }
        ?.map { readMerchantAt(it) }
        ?.sortedBy { it.name.lowercase() }
        .orEmpty()
}

/**
 * Adds [merchant] under the new [key] and returns it. The merchant's own key is ignored: the caller chooses ids so that
 * they are random and, in tests, predictable.
 */
fun addMerchant(doc: Document, key: String, merchant: Merchant): String {
    val tx = doc.startTransaction()
    tx.writeMerchant(tx.set(ObjectId.ROOT, key, ObjectType.MAP), key, merchant)
    tx.commit()
    return key
}

/** Replaces every field of the merchant at [key] with [merchant]'s. No-op, returning false, if the document has none there. */
fun updateMerchant(doc: Document, key: String, merchant: Merchant): Boolean {
    val tx = doc.startTransaction()
    val merchantId = (tx.get(ObjectId.ROOT, key).getOrNull() as? AmValue.Map)?.id
    if (merchantId == null) {
        tx.rollback()
        return false
    }
    tx.writeMerchant(merchantId, key, merchant)
    tx.commit()
    return true
}

/** Removes the merchant at [key]. No-op if there is none. */
fun removeMerchant(doc: Document, key: String) {
    val tx = doc.startTransaction()
    if (tx.get(ObjectId.ROOT, key).isPresent) {
        tx.delete(ObjectId.ROOT, key)
        tx.commit()
    } else {
        tx.rollback()
    }
}

private fun Transaction.writeMerchant(merchantId: ObjectId, key: String, merchant: Merchant) {
    set(merchantId, IdKey, key)
    set(merchantId, NameKey, merchant.name.trim())
    set(merchantId, TypeKey, merchant.type.name)
    set(merchantId, AddressKey, merchant.address.trim())
    set(merchantId, GersIdKey, merchant.gersId.trim())
    set(merchantId, AgentIdKey, merchant.x0xAgentId.trim())
    // A cleared coordinate must be removed, as writing over the same keys would leave the old one behind.
    setOrClear(merchantId, LatitudeKey, merchant.latitude.takeIf { merchant.hasLocation }?.let(::degreesToMicrodegrees))
    setOrClear(merchantId, LongitudeKey, merchant.longitude.takeIf { merchant.hasLocation }?.let(::degreesToMicrodegrees))

    val servicesId = set(merchantId, ServicesKey, ObjectType.LIST)
    val (known, custom) = splitServices(serviceNames(merchant.services, merchant.customServices))
    serviceNames(known, custom).forEachIndexed { index, name -> insert(servicesId, index.toLong(), name) }

    val hours = merchant.hours.normalised()
    set(merchantId, HoursNoteKey, hours.note)
    val hoursId = set(merchantId, HoursKey, ObjectType.MAP)
    hours.days.forEach { (day, ranges) ->
        val rangesId = set(hoursId, day.name, ObjectType.LIST)
        // Open and close minutes alternate: [open1, close1, open2, close2, ...].
        ranges.flatMap { listOf(it.openMinute, it.closeMinute) }.forEachIndexed { index, minute -> insert(rangesId, index.toLong(), minute) }
    }
}

private fun Transaction.setOrClear(obj: ObjectId, key: String, value: Int?) {
    when {
        value != null -> set(obj, key, value)
        get(obj, key).isPresent -> delete(obj, key)
    }
}

private fun Read.readMerchantAt(merchantId: ObjectId): Merchant {
    val (services, customServices) = splitServices(listStrings(merchantId, ServicesKey))
    val latitude = int(merchantId, LatitudeKey)
    val longitude = int(merchantId, LongitudeKey)
    val located = latitude != null && longitude != null
    return Merchant(
        key = str(merchantId, IdKey),
        name = str(merchantId, NameKey),
        type = MerchantType.entries.firstOrNull { it.name == str(merchantId, TypeKey) } ?: MerchantType.Other,
        services = services,
        customServices = customServices,
        hours = readHours(merchantId),
        latitude = latitude.takeIf { located }?.let(::microdegreesToDegrees),
        longitude = longitude.takeIf { located }?.let(::microdegreesToDegrees),
        address = str(merchantId, AddressKey),
        gersId = str(merchantId, GersIdKey),
        x0xAgentId = str(merchantId, AgentIdKey),
    )
}

private fun Read.readHours(merchantId: ObjectId): OpeningHours {
    val hoursId = (get(merchantId, HoursKey).getOrNull() as? AmValue.Map)?.id
    val days = hoursId?.let { id ->
        Weekday.entries.associateWith { day ->
            val rangesId = (get(id, day.name).getOrNull() as? AmValue.List)?.id
            val minutes = rangesId?.let { listItems(it).getOrNull() }?.mapNotNull { (it as? AmValue.Int)?.value?.toInt() }.orEmpty()
            minutes.chunked(2).filter { it.size == 2 }.map { OpenRange(it[0], it[1]) }
        }
    }.orEmpty()
    return OpeningHours(days, str(merchantId, HoursNoteKey)).normalised()
}

private fun Read.str(obj: ObjectId, key: String): String = (get(obj, key).getOrNull() as? AmValue.Str)?.value.orEmpty()

private fun Read.int(obj: ObjectId, key: String): Int? = (get(obj, key).getOrNull() as? AmValue.Int)?.value?.toInt()

private fun Read.listStrings(obj: ObjectId, key: String): List<String> {
    val listId = (get(obj, key).getOrNull() as? AmValue.List)?.id ?: return emptyList()
    return listItems(listId).getOrNull()?.mapNotNull { (it as? AmValue.Str)?.value }.orEmpty()
}
