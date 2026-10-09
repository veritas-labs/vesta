package uk.veritaslabs.vesta.automerge

import kotlin.jvm.optionals.getOrNull
import kotlin.math.roundToInt
import org.automerge.AmValue
import org.automerge.Document
import org.automerge.ObjectId
import org.automerge.ObjectType
import org.automerge.Read
import org.automerge.Transaction
import uk.veritaslabs.vesta.blobs.BlobReference
import uk.veritaslabs.vesta.blobs.BlobStore
import uk.veritaslabs.vesta.ui.screens.MediaKind
import uk.veritaslabs.vesta.ui.screens.Recipe
import uk.veritaslabs.vesta.ui.screens.RecipeIngredient
import uk.veritaslabs.vesta.ui.screens.RecipeMedia
import uk.veritaslabs.vesta.ui.screens.RecipeStep
import uk.veritaslabs.vesta.ui.screens.RecipeType

private const val IdKey = "id"
private const val TitleKey = "title"
private const val DescriptionKey = "description"
private const val TypeKey = "type"
private const val ServingsKey = "servings"
private const val PrepKey = "prepMinutes"
private const val CookKey = "cookMinutes"
private const val RestKey = "restMinutes"
private const val RatingKey = "rating"
private const val FavouriteKey = "favourite"
private const val TagsKey = "tags"
private const val SourceKey = "source"
private const val NotesKey = "notes"
private const val MediaKey = "media"
private const val UriKey = "uri"
private const val KindKey = "kind"
private const val CaptionKey = "caption"
private const val HashKey = "hash"
private const val SizeKey = "size"
private const val MimeKey = "mime"
private const val WidthKey = "w"
private const val HeightKey = "h"
private const val BlurKey = "blur"
private const val IngredientsKey = "ingredients"
private const val NameKey = "name"
private const val MilliKey = "milli"
private const val UnitKey = "unit"
private const val NoteKey = "note"
private const val StepsKey = "steps"
private const val TextKey = "text"
private const val MinutesKey = "minutes"

private const val MilliPerUnit = 1000

/** Reads every recipe, ordered by title (case-insensitive). */
fun Read.readCookbook(): List<Recipe> {
    return mapEntries(ObjectId.ROOT).getOrNull()
        ?.mapNotNull { (it.value as? AmValue.Map)?.id }
        ?.map { readRecipeAt(it) }
        ?.sortedBy { it.title.lowercase() }
        .orEmpty()
}

/** Every blob the cookbook's recipes refer to, one reference per distinct hash (the first seen wins). */
fun Read.readCookbookBlobReferences(): Set<BlobReference> = readCookbook().blobReferences()

/** The blobs these recipes refer to, one reference per distinct hash. */
fun List<Recipe>.blobReferences(): Set<BlobReference> =
    flatMap { recipe -> recipe.media.mapNotNull { it.blob } }.distinctBy { it.hash }.toSet()

/** Reads the recipe with [key], or `null` if the cookbook has none. */
fun Read.readRecipe(key: String): Recipe? {
    val recipeId = (get(ObjectId.ROOT, key).getOrNull() as? AmValue.Map)?.id ?: return null
    return readRecipeAt(recipeId)
}

/**
 * Adds [recipe] under the new [key] and returns it. The recipe's own key is ignored: the caller chooses ids so that
 * they are random and, in tests, predictable.
 */
fun addRecipe(doc: Document, key: String, recipe: Recipe): String {
    val tx = doc.startTransaction()
    tx.writeRecipe(tx.set(ObjectId.ROOT, key, ObjectType.MAP), key, recipe)
    tx.commit()
    return key
}

/** Replaces every field of the recipe at [key] with [recipe]'s. No-op, returning false, if the cookbook has none there. */
fun updateRecipe(doc: Document, key: String, recipe: Recipe): Boolean {
    val tx = doc.startTransaction()
    val recipeId = (tx.get(ObjectId.ROOT, key).getOrNull() as? AmValue.Map)?.id
    if (recipeId == null) {
        tx.rollback()
        return false
    }
    tx.writeRecipe(recipeId, key, recipe)
    tx.commit()
    return true
}

/** Sets the stars (clamped to 0..5) of the recipe at [key], writing only that field. No-op if there is no such recipe. */
fun setRecipeRating(doc: Document, key: String, rating: Int) =
    writeRecipeField(doc, key) { id -> set(id, RatingKey, rating.coerceIn(0, 5)) }

/** Marks or unmarks the recipe at [key] as a favourite, writing only that field. No-op if there is no such recipe. */
fun setRecipeFavourite(doc: Document, key: String, favourite: Boolean) =
    writeRecipeField(doc, key) { id -> set(id, FavouriteKey, favourite) }

/** Removes the recipe at [key]. No-op if there is none. */
fun removeRecipe(doc: Document, key: String) {
    val tx = doc.startTransaction()
    if (tx.get(ObjectId.ROOT, key).isPresent) {
        tx.delete(ObjectId.ROOT, key)
        tx.commit()
    } else {
        tx.rollback()
    }
}

private fun writeRecipeField(doc: Document, key: String, write: Transaction.(ObjectId) -> Unit) {
    val tx = doc.startTransaction()
    val recipeId = (tx.get(ObjectId.ROOT, key).getOrNull() as? AmValue.Map)?.id
    if (recipeId == null) {
        tx.rollback()
        return
    }
    tx.write(recipeId)
    tx.commit()
}

private fun Transaction.writeRecipe(recipeId: ObjectId, key: String, recipe: Recipe) {
    set(recipeId, IdKey, key)
    set(recipeId, TitleKey, recipe.title)
    set(recipeId, DescriptionKey, recipe.description)
    set(recipeId, TypeKey, recipe.type.name)
    set(recipeId, ServingsKey, recipe.servings)
    set(recipeId, PrepKey, recipe.prepMinutes)
    set(recipeId, CookKey, recipe.cookMinutes)
    set(recipeId, RestKey, recipe.restMinutes)
    set(recipeId, RatingKey, recipe.rating.coerceIn(0, 5))
    set(recipeId, FavouriteKey, recipe.favourite)
    set(recipeId, SourceKey, recipe.source)
    set(recipeId, NotesKey, recipe.notes)

    val tagsId = set(recipeId, TagsKey, ObjectType.LIST)
    normalizeTags(recipe.tags).forEachIndexed { index, tag -> insert(tagsId, index.toLong(), tag) }

    val mediaId = set(recipeId, MediaKey, ObjectType.LIST)
    recipe.media.forEachIndexed { index, media ->
        val entry = insert(mediaId, index.toLong(), ObjectType.MAP)
        set(entry, UriKey, media.uri)
        set(entry, KindKey, media.kind.name)
        set(entry, CaptionKey, media.caption)
        media.blob?.let { blob ->
            set(entry, HashKey, blob.hash)
            set(entry, SizeKey, blob.size.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())
            set(entry, MimeKey, blob.mime)
            set(entry, WidthKey, blob.w)
            set(entry, HeightKey, blob.h)
            set(entry, BlurKey, blob.blur)
        }
    }

    val ingredientsId = set(recipeId, IngredientsKey, ObjectType.LIST)
    recipe.ingredients.forEachIndexed { index, ingredient ->
        val entry = insert(ingredientsId, index.toLong(), ObjectType.MAP)
        set(entry, NameKey, ingredient.name)
        ingredient.quantity?.let { set(entry, MilliKey, (it * MilliPerUnit).roundToInt()) }
        set(entry, UnitKey, ingredient.unit)
        set(entry, NoteKey, ingredient.note)
    }

    val stepsId = set(recipeId, StepsKey, ObjectType.LIST)
    recipe.steps.forEachIndexed { index, step ->
        val entry = insert(stepsId, index.toLong(), ObjectType.MAP)
        set(entry, IdKey, step.id)
        set(entry, TextKey, step.text)
        step.minutes?.let { set(entry, MinutesKey, it) }
    }
}

private fun Read.readRecipeAt(recipeId: ObjectId): Recipe = Recipe(
    key = str(recipeId, IdKey),
    title = str(recipeId, TitleKey),
    description = str(recipeId, DescriptionKey),
    type = RecipeType.entries.firstOrNull { it.name == str(recipeId, TypeKey) } ?: RecipeType.Other,
    servings = int(recipeId, ServingsKey) ?: 0,
    prepMinutes = int(recipeId, PrepKey) ?: 0,
    cookMinutes = int(recipeId, CookKey) ?: 0,
    restMinutes = int(recipeId, RestKey) ?: 0,
    rating = (int(recipeId, RatingKey) ?: 0).coerceIn(0, 5),
    favourite = (get(recipeId, FavouriteKey).getOrNull() as? AmValue.Bool)?.value ?: false,
    media = listMaps(recipeId, MediaKey).map { readMedia(it) },
    ingredients = listMaps(recipeId, IngredientsKey).map {
        RecipeIngredient(
            name = str(it, NameKey),
            quantity = int(it, MilliKey)?.let { milli -> milli.toDouble() / MilliPerUnit },
            unit = str(it, UnitKey),
            note = str(it, NoteKey),
        )
    },
    steps = listMaps(recipeId, StepsKey).map {
        RecipeStep(
            id = str(it, IdKey),
            text = str(it, TextKey),
            minutes = int(it, MinutesKey),
        )
    },
    tags = listStrings(recipeId, TagsKey),
    source = str(recipeId, SourceKey),
    notes = str(recipeId, NotesKey),
)

private fun Read.readMedia(entry: ObjectId): RecipeMedia {
    val kind = MediaKind.entries.firstOrNull { kind -> kind.name == str(entry, KindKey) } ?: MediaKind.Photo
    // An entry is a blob only if it carries a well-formed hash; anything else (a legacy link, a damaged entry) stays a link.
    val hash = str(entry, HashKey).takeIf(BlobStore::isHash)
    val blob = hash?.let {
        BlobReference(
            hash = it,
            size = int(entry, SizeKey)?.toLong() ?: 0L,
            mime = str(entry, MimeKey),
            kind = kind,
            w = int(entry, WidthKey) ?: 0,
            h = int(entry, HeightKey) ?: 0,
            blur = str(entry, BlurKey),
        )
    }
    return RecipeMedia(uri = str(entry, UriKey), kind = kind, caption = str(entry, CaptionKey), blob = blob)
}

private fun Read.str(obj: ObjectId, key: String): String = (get(obj, key).getOrNull() as? AmValue.Str)?.value.orEmpty()

private fun Read.int(obj: ObjectId, key: String): Int? = (get(obj, key).getOrNull() as? AmValue.Int)?.value?.toInt()

private fun Read.listMaps(obj: ObjectId, key: String): List<ObjectId> {
    val listId = (get(obj, key).getOrNull() as? AmValue.List)?.id ?: return emptyList()
    return listItems(listId).getOrNull()?.mapNotNull { (it as? AmValue.Map)?.id }.orEmpty()
}

private fun Read.listStrings(obj: ObjectId, key: String): List<String> {
    val listId = (get(obj, key).getOrNull() as? AmValue.List)?.id ?: return emptyList()
    return listItems(listId).getOrNull()?.mapNotNull { (it as? AmValue.Str)?.value }.orEmpty()
}
