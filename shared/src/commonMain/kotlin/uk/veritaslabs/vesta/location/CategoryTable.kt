package uk.veritaslabs.vesta.location

/** The places category table (VGC1): top-level groups and the categories under them, most common first. */
class CategoryTable internal constructor(
    /** Raw group keys, e.g. "food_and_drink". */
    val groupKeys: List<String>,
    /** Raw category keys, e.g. "hair_salon". */
    val categoryKeys: List<String>,
    private val groupOfCategory: List<Int>,
) {
    val groupCount: Int get() = groupKeys.size
    val categoryCount: Int get() = categoryKeys.size

    /** "Hair Salon" for "hair_salon". */
    fun categoryName(index: Int): String = categoryKeys[checked(index, categoryCount)].split('_').filter { it.isNotEmpty() }
        .joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }

    /** "Food and drink" for "food_and_drink": sentence case. */
    fun groupName(group: Int): String = groupKeys[checked(group, groupCount)].split('_').filter { it.isNotEmpty() }
        .joinToString(" ").replaceFirstChar(Char::uppercaseChar)

    /** Group index of a category. */
    fun groupOf(categoryIndex: Int): Int = groupOfCategory[checked(categoryIndex, categoryCount)]

    private fun checked(i: Int, size: Int): Int {
        require(i in 0 until size) { "Index $i is outside the category table ($size entries)" }
        return i
    }

    companion object {
        private const val MAGIC = "VGC1"

        fun parse(compressed: ByteArray, inflater: Inflater): CategoryTable {
            val reader = ByteReader(inflater.inflate(compressed))
            require(reader.ascii(4) == MAGIC) { "Not a places category table" }
            val groupCount = reader.varint()
            require(groupCount <= reader.remaining) { "Category table is truncated" }
            val groups = List(groupCount) { reader.cString() }
            val catCount = reader.varint()
            require(catCount <= reader.remaining) { "Category table is truncated" }
            val cats = List(catCount) { reader.cString() }
            val parents = List(catCount) { reader.varint() }
            require(parents.all { it < groupCount }) { "Category refers to a group that does not exist" }
            require(reader.remaining == 0) { "Category table has ${reader.remaining} trailing bytes" }
            return CategoryTable(groups, cats, parents)
        }
    }
}
