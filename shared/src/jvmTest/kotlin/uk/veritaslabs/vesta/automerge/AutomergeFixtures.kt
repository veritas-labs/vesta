package uk.veritaslabs.vesta.automerge

import java.io.File
import java.util.Date
import kotlin.jvm.optionals.getOrNull
import org.automerge.AmValue
import org.automerge.Document
import org.automerge.NewValue
import org.automerge.ObjectId
import org.automerge.ObjectType
import org.automerge.Transaction
import uk.veritaslabs.vesta.blobs.BlobReference
import uk.veritaslabs.vesta.blobs.BlobStore
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.CalendarEvent
import uk.veritaslabs.vesta.calendar.EventCalendar
import uk.veritaslabs.vesta.calendar.EventRepeat
import uk.veritaslabs.vesta.home.StoredHome
import uk.veritaslabs.vesta.tasks.Task
import uk.veritaslabs.vesta.tasks.TaskDone
import uk.veritaslabs.vesta.tasks.TaskList
import uk.veritaslabs.vesta.ui.screens.MealSlot
import uk.veritaslabs.vesta.blobs.MediaKind
import uk.veritaslabs.vesta.ui.screens.Merchant
import uk.veritaslabs.vesta.ui.screens.MerchantService
import uk.veritaslabs.vesta.ui.screens.MerchantType
import uk.veritaslabs.vesta.ui.screens.OpenRange
import uk.veritaslabs.vesta.ui.screens.OpeningHours
import uk.veritaslabs.vesta.ui.screens.Recipe
import uk.veritaslabs.vesta.ui.screens.RecipeIngredient
import uk.veritaslabs.vesta.ui.screens.RecipeMedia
import uk.veritaslabs.vesta.ui.screens.RecipeStep
import uk.veritaslabs.vesta.ui.screens.RecipeType
import uk.veritaslabs.vesta.ui.screens.Weekday

/**
 * The checked-in Automerge fixtures (ADR-034 "Tests"): real document bytes in the shapes Vesta has shipped, loaded by the
 * tests with the current code. See `src/jvmTest/resources/fixtures/automerge/README.md` for what each file is and how it was
 * derived.
 *
 * The bytes in the repository are the fixtures. [generators] only records how they were made, so a new shape can be added;
 * regenerating is done by [RegenerateAutomergeFixtures], never by a normal test run, and a released fixture is never replaced.
 */
object AutomergeFixtures {

    private const val Directory = "fixtures/automerge"

    /** Where the fixtures live in the source tree, relative to the `shared` module (the working directory of its tests). */
    val sourceDirectory: File get() = File("src/jvmTest/resources/$Directory")

    /** The bytes of the fixture [name] (e.g. `pantry-current.automerge`) from the test classpath. */
    fun bytes(name: String): ByteArray =
        (AutomergeFixtures::class.java.getResourceAsStream("/$Directory/$name") ?: error("Missing fixture $name")).use { it.readBytes() }

    fun load(name: String): Document = Document.load(bytes(name))

    /** Every fixture, by file name, and the code that produced it. Old shapes are written with the raw Automerge API. */
    val generators: Map<String, () -> ByteArray> = linkedMapOf(
        "pantry-v1-no-registry.automerge" to ::pantryNoRegistry,
        "pantry-categories-unversioned.automerge" to ::pantryCategoriesUnversioned,
        "pantry-categories-marker-1.automerge" to ::pantryCategoriesMarker1,
        "pantry-current.automerge" to ::pantryCurrent,
        "pantry-extra-keys.automerge" to { withExtras(pantryCurrent(), ::pantryExtras) },
        "shopping-list-v1-no-tags.automerge" to ::shoppingListNoTags,
        "shopping-list-current.automerge" to ::shoppingListCurrent,
        "shopping-list-extra-keys.automerge" to { withExtras(shoppingListCurrent()) { entriesExtras(it, "items") } },
        "merchants-current.automerge" to ::merchantsCurrent,
        "merchants-extra-keys.automerge" to { withExtras(merchantsCurrent(), ::merchantsExtras) },
        "cookbook-v1-staged-steps.automerge" to ::cookbookStagedSteps,
        "cookbook-current.automerge" to ::cookbookCurrent,
        "cookbook-extra-keys.automerge" to { withExtras(cookbookCurrent(), ::rootEntriesExtras) },
        "mealplan-v1-no-eaters.automerge" to ::mealPlanNoEaters,
        "mealplan-current.automerge" to ::mealPlanCurrent,
        "mealplan-extra-keys.automerge" to { withExtras(mealPlanCurrent(), ::mealPlanExtras) },
        "calendar-current.automerge" to ::calendarCurrent,
        "calendar-extra-keys.automerge" to { withExtras(calendarCurrent(), ::calendarExtras) },
        "tasks-current.automerge" to ::tasksCurrent,
        "tasks-extra-keys.automerge" to { withExtras(tasksCurrent(), ::rootEntriesExtras) },
        "roster-v1-single-hub.automerge" to ::rosterSingleHub,
        "roster-current.automerge" to ::rosterCurrent,
        "roster-extra-keys.automerge" to { withExtras(rosterCurrent(), ::rosterExtras) },
        "home-registry-current.automerge" to ::homeRegistryCurrent,
        "home-registry-extra-keys.automerge" to { withExtras(homeRegistryCurrent(), ::rootEntriesExtras) },
        "profile-registry-current.automerge" to ::profileRegistryCurrent,
        "profile-registry-extra-keys.automerge" to { withExtras(profileRegistryCurrent(), ::rootEntriesExtras) },
    )

    // ---- Pantry ----------------------------------------------------------------------------------------------------

    /** The 15 primaries every Pantry registry held between ADR-010 and the 21-aisle reset (commit 886b9dc^), no version marker. */
    private val OriginalCategories = mapOf(
        "Fruit & Vegetables" to listOf("Fruit", "Vegetables", "Salad", "Herbs"),
        "Meat" to listOf("Beef", "Pork", "Poultry", "Lamb", "Sausages & Bacon"),
        "Fish" to listOf("Fresh Fish", "Frozen Fish", "Tinned Fish"),
        "Dairy" to listOf("Milk", "Cheese", "Yoghurt", "Butter & Spreads", "Eggs", "Cream"),
        "Bakery" to listOf("Bread", "Rolls & Wraps", "Cakes & Pastries"),
        "Condiments" to listOf("Ketchup", "Brown Sauce", "Mayonnaise", "Mustard", "Pickles"),
        "Cooking Sauces" to listOf("Pasta Sauce", "Curry Sauce", "Stock & Gravy"),
        "Tins & Jars" to listOf("Tinned Vegetables", "Tinned Tomatoes", "Beans & Pulses", "Soup"),
        "Pasta, Rice & Grains" to listOf("Pasta", "Rice", "Noodles", "Cereal", "Oats"),
        "Baking" to listOf("Flour", "Sugar", "Baking Ingredients"),
        "Herbs & Spices" to listOf("Herbs", "Spices", "Salt & Pepper"),
        "Snacks" to listOf("Crisps", "Biscuits", "Chocolate & Sweets", "Nuts"),
        "Frozen" to listOf("Frozen Meals", "Frozen Vegetables", "Ice Cream", "Frozen Chips"),
        "Drinks" to listOf("Tea & Coffee", "Juice", "Soft Drinks", "Water", "Alcohol"),
        "Household" to listOf("Cleaning", "Laundry", "Paper Goods", "Toiletries"),
    )

    /** Pantry as first shipped (commit 6260f05): an `items` map and nothing else; no category registry, no merchant keys. */
    private fun pantryNoRegistry(): ByteArray = build { tx ->
        val items = tx.set(ObjectId.ROOT, "items", ObjectType.MAP)
        tx.rawPantryItem(items, "whole milk", "Dairy", "Milk", "Whole Milk", "2 pint", null, 3, "2026-01-05T10:00:00Z", 2)
        tx.rawPantryItem(items, "5000169000000", "Tins & Jars", "Beans & Pulses", "Baked Beans", "415g", "5000169000000", 4, "2026-01-07T09:30:00Z", 0)
        tx.rawPantryItem(items, "beef mince", "Meat", null, "Beef Mince", "500g", null, 1, "2026-01-02T18:00:00Z", 1)
    }

    /** Pantry after ADR-010 and before the `categoriesVersion` marker: the 15-primary registry plus a user-added category, no marker. */
    private fun pantryCategoriesUnversioned(): ByteArray = build { tx ->
        val items = tx.set(ObjectId.ROOT, "items", ObjectType.MAP)
        tx.rawPantryItem(items, "whole milk", "Dairy", "Milk", "Whole Milk", "2 pint", null, 3, "2026-01-05T10:00:00Z", 2)
        tx.rawPantryItem(items, "dog biscuits", "Pet Supplies", "Treats", "Dog Biscuits", "1kg", null, 2, "2026-01-06T10:00:00Z", 1)
        tx.rawCategories(OriginalCategories + ("Pet Supplies" to listOf("Treats")))
    }

    /**
     * SYNTHETIC. No release ever wrote `categoriesVersion = 1` (the marker arrived with the value 2), but ADR-034 asks for a
     * document "at version 1": a short registry with an explicit marker of 1 and a custom category.
     */
    private fun pantryCategoriesMarker1(): ByteArray = build { tx ->
        val items = tx.set(ObjectId.ROOT, "items", ObjectType.MAP)
        tx.rawPantryItem(items, "whole milk", "Dairy", "Milk", "Whole Milk", "2 pint", null, 3, "2026-01-05T10:00:00Z", 2)
        tx.rawCategories(mapOf("Dairy" to listOf("Milk", "Cheese"), "Meat" to listOf("Beef"), "Allotment" to listOf("Beans")))
        tx.set(ObjectId.ROOT, "categoriesVersion", 1)
    }

    /** Pantry as the current build writes it: `categoriesVersion = 2`, custom categories, items with and without merchant keys. */
    private fun pantryCurrent(): ByteArray {
        val doc = Document.load(newPantryDocument())
        var clock = 0
        val now = { "2026-02-0${++clock}T08:00:00Z" }
        upsertPantryItem(doc, "Dairy & Eggs", "Milk", "Whole Milk", "2 pint", null, now)
        upsertPantryItem(doc, "Dairy & Eggs", "Milk", "Whole Milk", "2 pint", null, now)
        adjustStock(doc, "whole milk", 2)
        ensurePantryCategories(doc, "Dairy & Eggs", "Goat Milk")
        upsertPantryItem(doc, "Tins & Jars", "Beans & Pulses", "Baked Beans", "415g", "5000169000000", now)
        setPantryItemMerchants(doc, "5000169000000", listOf("aldi", "tesco"))
        upsertPantryItem(doc, "Household", null, "Bin Bags", "20 pack", null, now)
        ensurePantryCategories(doc, "Allotment", null)
        upsertPantryItem(doc, "Allotment", null, "Runner Bean Seeds", "1 packet", null, now)
        setPantryItemMerchants(doc, "runner bean seeds", listOf("garden-centre"))
        return doc.save()
    }

    /** Unknown root keys (scalar, map), unknown fields on every item and category, and a `schemaVersion` as ADR-034 will write it. */
    private fun pantryExtras(tx: Transaction) {
        tx.set(ObjectId.ROOT, "futureSetting", "on")
        tx.set(ObjectId.ROOT, "schemaVersion", 3)
        tx.set(tx.set(ObjectId.ROOT, "futureMap", ObjectType.MAP), "a", "b")
        entriesExtras(tx, "items")
        val categories = tx.mapId(ObjectId.ROOT, "categories") ?: return
        tx.mapEntryIds(categories).forEach { category ->
            tx.set(category, "futureCategoryField", "x")
            tx.set(category, "colour", 3)
        }
    }

    // ---- Shopping list ---------------------------------------------------------------------------------------------

    /** The shopping list as first shipped (commit 35a9157): items without tags, progress or out-of-stock. */
    private fun shoppingListNoTags(): ByteArray = build { tx ->
        val items = tx.set(ObjectId.ROOT, "items", ObjectType.MAP)
        tx.rawShoppingItem(items, "whole milk", "Dairy", "Milk", "Whole Milk", 2, "2 pint", null)
        tx.rawShoppingItem(items, "5000169000000", "Tins & Jars", "Beans & Pulses", "Baked Beans", 4, "415g", "5000169000000")
        tx.rawShoppingItem(items, "bin bags", "Household", null, "Bin Bags", 1, "20 pack", null)
    }

    private fun shoppingListCurrent(): ByteArray {
        val doc = Document.load(newShoppingListDocument())
        addShoppingItem(doc, "Dairy", "Milk", "Whole Milk", 2, "2 pint", null)
        setShoppingItemProgress(doc, "whole milk", got = 1, outOfStock = false)
        addShoppingItem(doc, "Tins & Jars", "Beans & Pulses", "Baked Beans", 4, "415g", "5000169000000", tags = listOf("offer", "bbq"))
        setShoppingItemProgress(doc, "5000169000000", got = 4, outOfStock = false)
        addShoppingItem(doc, "Household", null, "Bin Bags", 1, "20 pack", null)
        setShoppingItemProgress(doc, "bin bags", got = 0, outOfStock = true)
        addShoppingItem(doc, "Bakery", "Bread", "Sourdough", 1, "800g", null, tags = listOf("Saturday"))
        return doc.save()
    }

    // ---- Merchants -------------------------------------------------------------------------------------------------

    private fun merchantsCurrent(): ByteArray {
        val doc = Document()
        addMerchant(
            doc, "aldi",
            Merchant(
                name = "Aldi", type = MerchantType.NationalChain,
                services = listOf(MerchantService.Groceries, MerchantService.Bakery), customServices = listOf("Click & collect"),
                hours = OpeningHours(
                    days = mapOf(
                        Weekday.Monday to listOf(OpenRange(480, 1320)),
                        Weekday.Saturday to listOf(OpenRange(480, 720), OpenRange(780, 1200)),
                    ),
                    note = "Closed Sunday",
                ),
                latitude = 51.5074, longitude = -0.1278, address = "1 High Street", gersId = "08f194ad1b2c3d4e", x0xAgentId = "ab".repeat(32),
            ),
        )
        addMerchant(doc, "dave-veg", Merchant(name = "Dave's Veg Stall", type = MerchantType.MarketTrader))
        addMerchant(doc, "online-grocer", Merchant(name = "Online Grocer", type = MerchantType.OnlineRetailer, services = listOf(MerchantService.Groceries), address = "web"))
        return doc.save()
    }

    /** Unknown fields inside a merchant, an unknown weekday, and a merchant whose type and service this version has never heard of. */
    private fun merchantsExtras(tx: Transaction) {
        rootEntriesExtras(tx)
        tx.mapEntryIds(ObjectId.ROOT).forEach { merchant -> tx.mapId(merchant, "hours")?.let { tx.set(it, "Funday", ObjectType.LIST) } }
        val future = tx.set(ObjectId.ROOT, "future-merchant", ObjectType.MAP)
        tx.set(future, "id", "future-merchant")
        tx.set(future, "name", "Drone Depot")
        tx.set(future, "type", "DroneDelivery")
        tx.stringList(future, "services", "TeleportBooth")
    }

    // ---- Cookbook --------------------------------------------------------------------------------------------------

    /**
     * Recipes as the first Cookbook commits wrote them (b8064b5/4b4596f): every step carries an `after` list of prerequisite step
     * ids (dropped in b6a8789), and media are plain links with no blob. The current reader must ignore `after` and keep the steps.
     */
    private fun cookbookStagedSteps(): ByteArray = build { tx ->
        val recipe = tx.set(ObjectId.ROOT, "soup", ObjectType.MAP)
        tx.set(recipe, "id", "soup")
        tx.set(recipe, "title", "Tomato Soup")
        tx.set(recipe, "description", "Quick")
        tx.set(recipe, "type", "Starter")
        tx.set(recipe, "servings", 2)
        tx.set(recipe, "prepMinutes", 5)
        tx.set(recipe, "cookMinutes", 20)
        tx.set(recipe, "restMinutes", 0)
        tx.set(recipe, "rating", 4)
        tx.set(recipe, "favourite", false)
        tx.set(recipe, "source", "Gran")
        tx.set(recipe, "notes", "")
        tx.stringList(recipe, "tags", "soup")
        val media = tx.insert(tx.set(recipe, "media", ObjectType.LIST), 0, ObjectType.MAP)
        tx.set(media, "uri", "https://example.com/soup.jpg")
        tx.set(media, "kind", "Photo")
        tx.set(media, "caption", "Finished")
        val ingredient = tx.insert(tx.set(recipe, "ingredients", ObjectType.LIST), 0, ObjectType.MAP)
        tx.set(ingredient, "name", "Tomatoes")
        tx.set(ingredient, "milli", 800_000)
        tx.set(ingredient, "unit", "g")
        tx.set(ingredient, "note", "ripe")
        val steps = tx.set(recipe, "steps", ObjectType.LIST)
        val first = tx.insert(steps, 0, ObjectType.MAP)
        tx.set(first, "id", "s1")
        tx.set(first, "text", "Chop")
        tx.stringList(first, "after", "none")
        val second = tx.insert(steps, 1, ObjectType.MAP)
        tx.set(second, "id", "s2")
        tx.set(second, "text", "Simmer")
        tx.set(second, "minutes", 20)
        tx.stringList(second, "after", "s1")
    }

    /** The hash of the photo blob the current Cookbook fixture refers to. */
    val PhotoHash: String = BlobStore.sha256Hex("fixture photo".toByteArray())

    private fun cookbookCurrent(): ByteArray {
        val doc = Document()
        addRecipe(
            doc, "bolognese",
            Recipe(
                title = "Spaghetti Bolognese", description = "Sunday classic", type = RecipeType.Dinner, servings = 4,
                prepMinutes = 15, cookMinutes = 90, restMinutes = 5, rating = 5, favourite = true,
                media = listOf(
                    RecipeMedia("", MediaKind.Photo, "On the table", BlobReference(PhotoHash, 2048L, "image/jpeg", MediaKind.Photo, 800, 600, "LEHV6nWB2yk8")),
                    RecipeMedia("https://example.com/video", MediaKind.Video, "Technique"),
                ),
                ingredients = listOf(
                    RecipeIngredient("Minced beef", 500.0, "g", "lean"),
                    RecipeIngredient("Spaghetti", 400.0, "g"),
                    RecipeIngredient("Salt"),
                ),
                steps = listOf(RecipeStep("s1", "Brown the mince", 10), RecipeStep("s2", "Simmer"), RecipeStep("s3", "Cook the pasta", 11)),
                tags = listOf("Italian", "family"), source = "Nonna", notes = "Better the next day",
            ),
        )
        addRecipe(doc, "pancakes", Recipe(title = "Pancakes", type = RecipeType.Breakfast, servings = 2, ingredients = listOf(RecipeIngredient("Flour", 1.5, "cups"))))
        addRecipe(doc, "toast", Recipe(title = "Toast", type = RecipeType.Other, servings = 1))
        return doc.save()
    }

    // ---- Meal plan -------------------------------------------------------------------------------------------------

    private val Monday = CalendarDate(2026, 10, 12)
    private val Tuesday = CalendarDate(2026, 10, 13)

    /** The meal plan as first shipped (commit 061d0eb): only `date|Slot|recipe` keys holding a timestamp. */
    private fun mealPlanNoEaters(): ByteArray = build { tx ->
        tx.set(ObjectId.ROOT, "2026-10-12|Dinner|bolognese", Date(1_000))
        tx.set(ObjectId.ROOT, "2026-10-12|Dinner|salad", Date(2_000))
        tx.set(ObjectId.ROOT, "2026-10-13|Breakfast|pancakes", Date(3_000))
    }

    private fun mealPlanCurrent(): ByteArray {
        val doc = Document()
        planMeal(doc, Monday, MealSlot.Dinner, "bolognese", 1_000)
        planMeal(doc, Monday, MealSlot.Dinner, "salad", 2_000)
        planMeal(doc, Tuesday, MealSlot.Breakfast, "pancakes", 3_000)
        planMeal(doc, Tuesday, MealSlot.Lunch, "soup", 4_000)
        addMealGuest(doc, "g1", "Grandad")
        addMealGuest(doc, "g2", "Ben")
        chooseEaters(doc, Monday, MealSlot.Dinner, "bolognese", setOf("m:mum", "p:g1"))
        return doc.save()
    }

    /** A slot, a date and a prefix this version doesn't know, plus unknown scalar root keys. */
    private fun mealPlanExtras(tx: Transaction) {
        tx.set(ObjectId.ROOT, "schemaVersion", 3)
        tx.set(ObjectId.ROOT, "2026-10-14|Brunch|pancakes", Date(5_000))
        tx.set(ObjectId.ROOT, "2026-13-45|Dinner|pancakes", Date(5_000))
        tx.set(ObjectId.ROOT, "snack|2026-10-14|Dinner|pancakes", true)
        tx.set(ObjectId.ROOT, "futureSetting", "on")
    }

    // ---- Calendar --------------------------------------------------------------------------------------------------

    private fun calendarCurrent(): ByteArray {
        val doc = Document()
        putCalendarEvent(doc, CalendarEvent("e1", EventCalendar.Home, "Dentist", false, 1_791_000_000_000, 1_791_003_600_000, location = "High St", description = "Check-up", color = 2))
        putCalendarEvent(doc, CalendarEvent("e2", EventCalendar.Home, "Birthday", true, 1_791_072_000_000, 1_791_072_000_000, repeat = EventRepeat.Yearly, participants = setOf("m:mum", "p:g1")))
        putCalendarEvent(doc, CalendarEvent("e3", EventCalendar.Home, "Bins", false, 1_791_100_000_000, 1_791_101_800_000, repeat = EventRepeat.Weekly))
        return doc.save()
    }

    /** Unknown fields, an unknown repeat value and colour, and an event with no readable start (skipped on purpose). */
    private fun calendarExtras(tx: Transaction) {
        rootEntriesExtras(tx)
        val e3 = tx.mapId(ObjectId.ROOT, "e3") ?: return
        tx.set(e3, "repeat", "Fortnightly")
        tx.set(e3, "color", "99")
        val broken = tx.set(ObjectId.ROOT, "broken", ObjectType.MAP)
        tx.set(broken, "title", "No start")
    }

    // ---- Tasks -----------------------------------------------------------------------------------------------------

    private fun tasksCurrent(): ByteArray {
        val doc = Document()
        putTask(doc, Task("t1", TaskList.Home, "Take the bins out", due = CalendarDate(2026, 10, 15), timeMinute = 1140, repeat = EventRepeat.Weekly, assignees = setOf("m:mum"), createdMillis = 1_790_000_000_000, createdBy = "m:mum"))
        putTask(doc, Task("t2", TaskList.Home, "Fix the shed", description = "Roof felt"))
        putTask(doc, Task("t3", TaskList.Home, "Water plants", due = CalendarDate(2026, 10, 14), repeat = EventRepeat.Daily, every = 3))
        putTaskDone(doc, TaskDone("t1", "2026-10-08", 1_790_100_000_000, "m:mum"))
        putTaskDone(doc, TaskDone("t2", "anytime", 1_790_200_000_000, "m:dad"))
        return doc.save()
    }

    // ---- Home roster -----------------------------------------------------------------------------------------------

    /** The roster before several hubs, device names and unpairing: members, device claims, the group, one `hub` scalar and an invite. */
    private fun rosterSingleHub(): ByteArray = build { tx ->
        tx.set(ObjectId.ROOT, "member:mum:name", "Mum")
        tx.set(ObjectId.ROOT, "member:mum:role", "admin")
        tx.set(ObjectId.ROOT, "member:mum:joinedAt", "1790000000000")
        tx.set(ObjectId.ROOT, "member:dad:name", "Dad")
        tx.set(ObjectId.ROOT, "member:dad:role", "member")
        tx.set(ObjectId.ROOT, "member:dad:joinedAt", "1790000100000")
        tx.set(ObjectId.ROOT, "device:agent-mum-phone", "mum")
        tx.set(ObjectId.ROOT, "device:agent-dad-phone", "dad")
        tx.set(ObjectId.ROOT, "group", "group-1")
        tx.set(ObjectId.ROOT, "hub", "agent-hub")
        tx.set(ObjectId.ROOT, "invite:hash1:createdBy", "mum")
        tx.set(ObjectId.ROOT, "invite:hash1:expiresAt", "1799999999999")
        tx.set(ObjectId.ROOT, "invite:hash1:maxUses", "2")
    }

    private fun rosterCurrent(): ByteArray {
        val doc = Document()
        putRosterMember(doc, "mum", "Mum", "avatar-1", "admin", 1_790_000_000_000)
        putRosterMember(doc, "dad", "Dad", null, "member", 1_790_000_100_000)
        putRosterMember(doc, "kid", "Kid", null, "member", 1_790_000_200_000)
        retireRosterMember(doc, "kid")
        claimRosterDevice(doc, "agent-mum-phone", "mum")
        claimRosterDevice(doc, "agent-mum-tablet", "mum")
        claimRosterDevice(doc, "agent-dad-phone", "dad")
        setRosterDeviceName(doc, "agent-mum-phone", "Mum's phone")
        markRosterGone(doc, "agent-mum-tablet", "mum")
        setRosterGroup(doc, "group-1")
        addRosterHub(doc, "agent-hub")
        addRosterHub(doc, "agent-mum-phone")
        putRosterInvite(doc, "hash1", "mum", 1_799_999_999_999, 2)
        useRosterInvite(doc, "hash1", "dad")
        putRosterInvite(doc, "hash2", "mum", 1_799_999_999_999, 1)
        revokeRosterInvite(doc, "hash2")
        return doc.save()
    }

    /** Unknown `member:` field, unknown prefixes, and an unknown invite field: all scalars, like the roster itself. */
    private fun rosterExtras(tx: Transaction) {
        tx.set(ObjectId.ROOT, "schemaVersion", 3)
        tx.set(ObjectId.ROOT, "member:mum:pronouns", "she/her")
        tx.set(ObjectId.ROOT, "futurething:abc", "x")
        tx.set(ObjectId.ROOT, "invite:hash1:note", "for the neighbours")
        tx.set(ObjectId.ROOT, "devicemodel:agent-mum-phone", "Pixel 9")
    }

    // ---- Registries ------------------------------------------------------------------------------------------------

    private fun homeRegistryCurrent(): ByteArray {
        val doc = Document()
        putRegistryHome(doc, StoredHome("home-1", "The Flat", "family", 1_790_000_000_000))
        putRegistryHome(doc, StoredHome("home-2", "Granny's", "couple", 1_790_000_500_000))
        putRegistryHome(doc, StoredHome("home-3", "Old House", "family", 1_780_000_000_000))
        deleteRegistryHome(doc, "home-3")
        return doc.save()
    }

    private fun profileRegistryCurrent(): ByteArray {
        val doc = Document()
        putProfile(doc, "p1", "Mark", "avatar-1", 1_790_000_000_000, named = true)
        putProfile(doc, "p2", "Phone 2", null, 1_790_000_900_000)
        return doc.save()
    }

    // ---- helpers ---------------------------------------------------------------------------------------------------

    private fun build(block: (Transaction) -> Unit): ByteArray {
        val doc = Document()
        val tx = doc.startTransaction()
        block(tx)
        tx.commit()
        return doc.save()
    }

    /** [current] with unknown keys added by [extras]: a later, "future" writer's additions on top of the shipped shape. */
    private fun withExtras(current: ByteArray, extras: (Transaction) -> Unit): ByteArray {
        val doc = Document.load(current)
        val tx = doc.startTransaction()
        extras(tx)
        tx.commit()
        return doc.save()
    }

    private fun Transaction.mapId(parent: ObjectId, key: String): ObjectId? = (get(parent, key).getOrNull() as? AmValue.Map)?.id

    private fun Transaction.mapEntryIds(parent: ObjectId): List<ObjectId> =
        mapEntries(parent).getOrNull().orEmpty().mapNotNull { (it.value as? AmValue.Map)?.id }

    private fun Transaction.stringList(parent: ObjectId, key: String, vararg values: String) {
        val list = set(parent, key, ObjectType.LIST)
        values.forEachIndexed { index, value -> insert(list, index.toLong(), value) }
    }

    /** `schemaVersion` plus unknown fields on every map directly under the document root. */
    private fun rootEntriesExtras(tx: Transaction) = entriesExtras(tx, null)

    /** `schemaVersion` plus unknown fields on every map directly under [container] (the document root when null). */
    private fun entriesExtras(tx: Transaction, container: String?) {
        tx.set(ObjectId.ROOT, "schemaVersion", 3)
        val parent = container?.let { tx.mapId(ObjectId.ROOT, it) } ?: ObjectId.ROOT
        tx.mapEntryIds(parent).forEach { entry ->
            tx.set(entry, "futureField", "x")
            tx.set(entry, "futureCount", NewValue.counter(7))
            tx.stringList(entry, "futureList", "y")
        }
    }

    private fun Transaction.rawPantryItem(
        items: ObjectId, key: String, primary: String, secondary: String?, title: String, packageSize: String, barcode: String?,
        useCount: Long, lastUsedAt: String, stock: Long,
    ) {
        val item = set(items, key, ObjectType.MAP)
        set(item, "id", key)
        set(item, "primaryCategory", primary)
        secondary?.let { set(item, "secondaryCategory", it) }
        set(item, "title", title)
        set(item, "packageSize", packageSize)
        barcode?.let { set(item, "barcode", it) }
        set(item, "useCount", NewValue.counter(useCount))
        set(item, "lastUsedAt", lastUsedAt)
        set(item, "stockCount", NewValue.counter(stock))
    }

    private fun Transaction.rawCategories(categories: Map<String, List<String>>) {
        val root = set(ObjectId.ROOT, "categories", ObjectType.MAP)
        categories.forEach { (name, secondary) ->
            val category = set(root, categoryKey(name), ObjectType.MAP)
            set(category, "name", name)
            val secondaryId = set(category, "secondary", ObjectType.MAP)
            secondary.forEach { set(secondaryId, categoryKey(it), it) }
        }
    }

    private fun Transaction.rawShoppingItem(
        items: ObjectId, key: String, primary: String, secondary: String?, title: String, quantity: Int, packageSize: String, barcode: String?,
    ) {
        val item = set(items, key, ObjectType.MAP)
        set(item, "id", key)
        set(item, "primaryCategory", primary)
        secondary?.let { set(item, "secondaryCategory", it) }
        set(item, "title", title)
        set(item, "quantity", quantity)
        set(item, "packageSize", packageSize)
        barcode?.let { set(item, "barcode", it) }
    }
}
