package uk.veritaslabs.vesta.licenses

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import vesta.shared.generated.resources.Res

/** One third-party library Vesta ships with, and the licence(s) it is available under. */
@Serializable
data class OpenSourceLicense(
    val name: String,
    val version: String,
    val licenses: List<String>,
    val url: String = "",
)

private val LicensesJson = Json { ignoreUnknownKeys = true }

/** Parses the list written by `./gradlew generateLicenses`. */
fun parseOpenSourceLicenses(json: String): List<OpenSourceLicense> = LicensesJson.decodeFromString(json)

/** Reads the bundled third-party licence list (composeResources/files/licenses.json); empty if it is missing or unreadable. */
suspend fun loadOpenSourceLicenses(): List<OpenSourceLicense> =
    try {
        parseOpenSourceLicenses(Res.readBytes("files/licenses.json").decodeToString())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        emptyList()
    }
