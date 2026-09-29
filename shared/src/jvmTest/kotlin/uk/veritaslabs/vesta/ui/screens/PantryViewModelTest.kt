package uk.veritaslabs.vesta.ui.screens

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout

@OptIn(ExperimentalCoroutinesApi::class)
class PantryViewModelTest {

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakePantrySource : PantrySource {
        val flow = MutableSharedFlow<PantryContents>(replay = 1)
        override val contents = flow
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
        val viewModel = PantryViewModel(FakePantrySource())

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
        val viewModel = PantryViewModel(source)

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
        val viewModel = PantryViewModel(source)
        source.flow.emit(PantryContents(items = listOf(milk)))
        withTimeout(5_000) { viewModel.uiState.first { !it.isLoading } }

        // Act
        source.flow.emit(PantryContents(items = listOf(milk.copy(stockCount = 0))))
        val state = withTimeout(5_000) { viewModel.uiState.first { it.items.firstOrNull()?.stockCount == 0 } }

        // Assert
        assertEquals(0, state.items.single().stockCount)
    }
}
