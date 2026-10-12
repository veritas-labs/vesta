package uk.veritaslabs.vesta.ui.screens

import androidx.lifecycle.viewModelScope
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout

@OptIn(ExperimentalCoroutinesApi::class)
class PantryViewModelTest {

    private val viewModels = mutableListOf<PantryViewModel>()

    /**
     * A view model whose scope is ended in [tearDown]. Its `stateIn` keeps coroutines on `Dispatchers.Main` alive, and a
     * scope left running past `resetMain` later fails to dispatch (there is no real Main on the JVM), which kotlinx-coroutines-test
     * then blames on whichever unrelated test happens to start next.
     */
    private fun viewModelOf(source: PantrySource): PantryViewModel = PantryViewModel(source).also { viewModels += it }

    @AfterTest
    fun tearDown() {
        // Ended while Main is still set, so the cancellation can be dispatched and finish.
        runBlocking { viewModels.forEach { it.viewModelScope.coroutineContext.job.cancelAndJoin() } }
        viewModels.clear()
        Dispatchers.resetMain()
    }

    private class FakePantrySource : PantrySource {
        val flow = MutableSharedFlow<PantryContents>(replay = 1)
        override val contents = flow
        val saved = mutableListOf<PantryItemDetails>()
        override suspend fun save(item: PantryItemDetails) { saved += item }
        override suspend fun delete(key: String) {}
        val merchantChanges = mutableListOf<Pair<Set<String>, MerchantLinkChange>>()
        override suspend fun changeMerchants(itemKeys: Set<String>, change: MerchantLinkChange) { merchantChanges += itemKeys to change }
    }

    private val milk = PantryListItem(
        key = "whole milk",
        primaryCategory = "Dairy",
        secondaryCategory = "Milk",
        title = "Whole Milk",
        packageSize = "2 pint",
        stockCount = 1,
    )

    @Test
    fun startsLoadingBeforeTheSourceEmits() = runBlocking {
        // Arrange
        Dispatchers.setMain(Dispatchers.Default)
        val viewModel = viewModelOf(FakePantrySource())

        // Act
        val state = viewModel.uiState.value

        // Assert
        assertTrue(state.isLoading)
        assertTrue(state.items.isEmpty())
    }

    @Test
    fun exposesTheSourcesContentsOnceLoaded() = runBlocking {
        // Arrange
        Dispatchers.setMain(Dispatchers.Default)
        val source = FakePantrySource()
        val viewModel = viewModelOf(source)

        // Act
        source.flow.emit(PantryContents(items = listOf(milk), emptyCategories = listOf("Snacks")))
        val state = withTimeout(5_000) { viewModel.uiState.first { !it.isLoading } }

        // Assert
        assertEquals(PantryUiState(listOf(milk), listOf("Snacks"), isLoading = false), state)
    }

    @Test
    fun reflectsLaterChangesToThePantry() = runBlocking {
        // Arrange
        Dispatchers.setMain(Dispatchers.Default)
        val source = FakePantrySource()
        val viewModel = viewModelOf(source)
        source.flow.emit(PantryContents(items = listOf(milk)))
        withTimeout(5_000) { viewModel.uiState.first { !it.isLoading } }

        // Act
        source.flow.emit(PantryContents(items = listOf(milk.copy(stockCount = 0))))
        val state = withTimeout(5_000) { viewModel.uiState.first { it.items.firstOrNull()?.stockCount == 0 } }

        // Assert
        assertEquals(0, state.items.single().stockCount)
    }

    @Test
    fun changingMerchantsPassesTheSelectedKeysAndTheChangeToTheSource() = runBlocking {
        // Arrange
        Dispatchers.setMain(Dispatchers.Default)
        val source = FakePantrySource()
        val viewModel = viewModelOf(source)

        // Act
        viewModel.changeMerchants(setOf("whole milk"), MerchantLinkChange.Add(listOf("tesco")))
        withTimeout(5_000) { while (source.merchantChanges.isEmpty()) kotlinx.coroutines.delay(10); Unit }

        // Assert
        assertEquals<List<Pair<Set<String>, MerchantLinkChange>>>(listOf(setOf("whole milk") to MerchantLinkChange.Add(listOf("tesco"))), source.merchantChanges)
    }
}
