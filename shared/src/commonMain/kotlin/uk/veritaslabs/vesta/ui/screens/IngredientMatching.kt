package uk.veritaslabs.vesta.ui.screens

import kotlin.math.abs

/** What a unit measures; amounts only compare within one dimension. */
internal enum class UnitDimension { Mass, Volume, Count }

/** An amount in a dimension's base unit (grams, millilitres, or a plain count). */
internal data class Measure(val dimension: UnitDimension, val amount: Double)

private val unitFactors: Map<String, Measure> = buildMap {
    fun add(dimension: UnitDimension, factor: Double, vararg names: String) = names.forEach { put(it, Measure(dimension, factor)) }
    add(UnitDimension.Mass, 1.0, "g", "gram", "gramme")
    add(UnitDimension.Mass, 1000.0, "kg", "kilo", "kilogram", "kilogramme")
    add(UnitDimension.Mass, 0.001, "mg", "milligram")
    add(UnitDimension.Mass, 28.3495, "oz", "ounce")
    add(UnitDimension.Mass, 453.592, "lb", "pound")
    add(UnitDimension.Volume, 1.0, "ml", "millilitre", "milliliter")
    add(UnitDimension.Volume, 10.0, "cl", "centilitre", "centiliter")
    add(UnitDimension.Volume, 100.0, "dl", "decilitre", "deciliter")
    add(UnitDimension.Volume, 1000.0, "l", "litre", "liter")
    add(UnitDimension.Volume, 5.0, "tsp", "teaspoon")
    add(UnitDimension.Volume, 15.0, "tbsp", "tbs", "tablespoon")
    add(UnitDimension.Volume, 250.0, "cup")
    add(UnitDimension.Volume, 28.4131, "floz", "fluidounce")
    add(UnitDimension.Volume, 568.261, "pint", "pt")
    add(UnitDimension.Count, 1.0, "", "x", "tin", "can", "pack", "packet", "jar", "bottle", "carton", "bag", "pcs", "pc", "piece")
}

private fun unitMeasure(unit: String): Measure? {
    val key = unit.lowercase().filter { it.isLetter() }
    return unitFactors[key] ?: unitFactors[key.removeSuffix("s")]
}

/** [quantity] of [unit] as a [Measure], or null when the unit isn't one we can compare (a pinch, a clove, ...). */
internal fun measureOf(quantity: Double, unit: String): Measure? =
    unitMeasure(unit)?.let { Measure(it.dimension, quantity * it.amount) }

private val packageSizePattern = Regex("""^\s*(?:(\d+)\s*[x×]\s*)?(\d+(?:\.\d+)?)\s*([a-zA-Z. ]*?)\s*$""")

/** What one Pantry unit holds, read from text like "400g", "1.5 l", "2 pint" or "4 x 330ml"; a blank size is one plain item. */
internal fun parsePackageSize(text: String): Measure? {
    if (text.isBlank()) return Measure(UnitDimension.Count, 1.0)
    val match = packageSizePattern.matchEntire(text) ?: return null
    val packs = match.groupValues[1].toIntOrNull() ?: 1
    val number = match.groupValues[2].toDoubleOrNull() ?: return null
    return measureOf(number * packs, match.groupValues[3])
}

private fun singular(word: String): String = when {
    word.length <= 3 -> word
    word.endsWith("ss") || word.endsWith("us") || word.endsWith("is") -> word
    word.endsWith("ies") -> word.dropLast(3) + "y"
    word.endsWith("oes") || word.endsWith("ches") || word.endsWith("shes") || word.endsWith("xes") || word.endsWith("sses") -> word.dropLast(2)
    word.endsWith("s") -> word.dropLast(1)
    else -> word
}

/** A name reduced for comparing: lower case, single spaces, every word singular ("Cherry Tomatoes" and "cherry tomato" agree). */
internal fun ingredientKey(name: String): String =
    name.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ") { singular(it) }

/** Whether [pantry] holds at least [need] of the ingredient, or null when the amounts can't be compared. */
internal fun holdsEnough(need: Measure, stocked: List<PantryListItem>): Boolean? {
    var have = 0.0
    stocked.forEach { item ->
        val size = parsePackageSize(item.packageSize)
        if (size == null || size.dimension != need.dimension) return null
        have += item.stockCount * size.amount
    }
    return have + abs(need.amount) * 1e-9 >= need.amount
}
