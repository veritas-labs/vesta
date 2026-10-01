package uk.veritaslabs.vesta.pantry

/**
 * A unit of measure in a package size. [symbol] is the canonical text; [attached] units are written without a space
 * after the amount ("500ml"), others with one ("2 pint"), matching what Pantry items already hold. Every alias is also
 * accepted with a trailing "s". See ADR-011.
 */
data class PackageUnit(val symbol: String, val attached: Boolean, val aliases: List<String> = emptyList())

/** A container a package comes in. [name] is the canonical text; aliases are also accepted with a trailing "s". */
data class PackageContainer(val name: String, val aliases: List<String> = emptyList())

/** The built-in measure units, most common first. */
val PackageUnits: List<PackageUnit> = listOf(
    PackageUnit("g", attached = true, aliases = listOf("gram", "gm")),
    PackageUnit("kg", attached = true, aliases = listOf("kilogram", "kilo")),
    PackageUnit("ml", attached = true, aliases = listOf("millilitre", "milliliter")),
    PackageUnit("l", attached = true, aliases = listOf("litre", "liter", "ltr", "lt")),
    PackageUnit("oz", attached = false, aliases = listOf("ounce")),
    PackageUnit("lb", attached = false, aliases = listOf("pound")),
    PackageUnit("fl oz", attached = false, aliases = listOf("floz", "fluid ounce")),
    PackageUnit("pint", attached = false, aliases = listOf("pt")),
    PackageUnit("gal", attached = false, aliases = listOf("gallon")),
)

/** The built-in containers, most common first. "can" is an alias of "tin". */
val PackageContainers: List<PackageContainer> = listOf(
    PackageContainer("tin", aliases = listOf("can")),
    PackageContainer("jar"),
    PackageContainer("bottle"),
    PackageContainer("packet", aliases = listOf("pkt", "package")),
    PackageContainer("box"),
    PackageContainer("bag"),
    PackageContainer("carton"),
    PackageContainer("tub"),
    PackageContainer("tube"),
    PackageContainer("pouch"),
    PackageContainer("sachet"),
    PackageContainer("tray"),
    PackageContainer("roll"),
    PackageContainer("pack", aliases = listOf("multipack")),
)

/**
 * A structured package size: [amount] plus a [unit] and/or a [container] — "500ml", "6 pack", "15 oz tin".
 * The Pantry stores only the canonical [format]ted string (ADR-011); this exists to build it, tidy up text typed by
 * hand or sent by an agent, and prefill the edit form.
 */
data class PackageSize(val amount: String, val unit: PackageUnit?, val container: PackageContainer?) {

    /** The canonical text, e.g. "500ml", "2 pint", "6 pack", "400g tin". */
    fun format(): String = buildString {
        append(amount)
        unit?.let {
            if (!it.attached) append(' ')
            append(it.symbol)
        }
        container?.let {
            append(' ')
            append(it.name)
        }
    }

    companion object {
        private val amountRegex = Regex("""^(\d+(?:[.,]\d+)?)\s*(.*)$""")

        /**
         * Reads [text] as an amount followed by a unit and/or container (case-insensitive, aliases and plurals
         * accepted). Null if there is no leading amount, nothing follows it, or anything is left unrecognised.
         */
        fun parse(text: String): PackageSize? {
            val match = amountRegex.matchEntire(text.trim().lowercase()) ?: return null
            val amount = match.groupValues[1].replace(',', '.')
            var rest = match.groupValues[2].replace('.', ' ').trim().replace(Regex("\\s+"), " ")
            if (rest.isEmpty()) return null

            val unitMatch = unitAliases.firstOrNull { (alias, _) -> rest == alias || rest.startsWith("$alias ") }
            if (unitMatch != null) rest = rest.removePrefix(unitMatch.first).trim()
            val container = if (rest.isEmpty()) null else containerAliases[rest] ?: return null
            return PackageSize(amount, unitMatch?.second, container)
        }

        /** [parse]d and re-[format]ted text, or [text] trimmed if it isn't recognised. */
        fun normalise(text: String): String = parse(text)?.format() ?: text.trim()

        // Longest alias first, so "fl oz" wins over a shorter prefix.
        private val unitAliases: List<Pair<String, PackageUnit>> = PackageUnits
            .flatMap { unit -> (listOf(unit.symbol) + unit.aliases).flatMap { listOf(it to unit, "${it}s" to unit) } }
            .sortedByDescending { it.first.length }

        private val containerAliases: Map<String, PackageContainer> = PackageContainers
            .flatMap { c -> (listOf(c.name) + c.aliases).flatMap { listOf(it to c, "${it}s" to c) } }
            .toMap()
    }
}
