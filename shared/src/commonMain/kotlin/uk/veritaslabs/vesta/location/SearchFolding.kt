package uk.veritaslabs.vesta.location

// Each group is a base letter followed by the accented forms folded onto it. Lower-case only; text is lower-cased first.
private val GROUPS = listOf(
    "aàáâãäåāăąǎ", "cçćĉċč", "dďđ", "eèéêëēĕėęěə", "gĝğġģ", "hĥħ", "iìíîïĩīĭįı", "jĵ", "kķ", "lĺļľŀł",
    "nñńņňŉ", "oòóôõöøōŏőǒ", "rŕŗř", "sśŝşš", "tţťŧ", "uùúûüũūŭůűųǔ", "wŵ", "yýÿŷ", "zźżž",
)

private val FOLD: Map<Char, Char> = buildMap {
    GROUPS.forEach { group -> group.drop(1).forEach { put(it, group[0]) } }
}

/**
 * Lower-cases and strips Latin diacritics with no ICU: Latin-1 and Latin Extended-A letters are folded onto their base
 * letter, a few ligatures are expanded (ß, æ, œ, þ) and combining marks are dropped. Anything else is left as it is.
 */
internal fun foldForSearch(text: String): String {
    val out = StringBuilder(text.length)
    for (c in text.lowercase()) {
        when (c) {
            'ß' -> out.append("ss")
            'æ' -> out.append("ae")
            'œ' -> out.append("oe")
            'þ' -> out.append("th")
            in '̀'..'ͯ' -> {}
            else -> out.append(FOLD[c] ?: c)
        }
    }
    return out.toString()
}
