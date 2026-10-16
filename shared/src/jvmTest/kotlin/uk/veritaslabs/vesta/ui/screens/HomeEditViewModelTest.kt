package uk.veritaslabs.vesta.ui.screens

import uk.veritaslabs.vesta.home.homeRepository
import uk.veritaslabs.vesta.home.HomeDetails
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import uk.veritaslabs.kits.store.testing.openInMemoryDatabase
import uk.veritaslabs.vesta.home.HomeRepository
import uk.veritaslabs.vesta.home.SelectedHome

@OptIn(ExperimentalCoroutinesApi::class)
class HomeEditViewModelTest {

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun creatingStartsBlankAndSavingPersistsANewHome() = runTest {
        // Arrange
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = homeRepository(openInMemoryDatabase())
        val viewModel = HomeEditViewModel(repository, SelectedHome())

        // Act
        val initial = viewModel.uiState.value
        viewModel.save(HomeDetails(name = "The Johnsons", shape = HomeShape.Family.storageValue))
        val state = viewModel.uiState.first { it.isFinished }

        // Assert
        assertNull(initial.home)
        assertFalse(initial.isLoading)
        assertEquals(listOf("The Johnsons"), repository.homes.first().map { it.name })
        assertTrue(state.isFinished)
    }

    @Test
    fun editingLoadsTheExistingHome() = runTest {
        // Arrange
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = homeRepository(openInMemoryDatabase())
        val id = repository.create("Flat 4B", HomeShape.Flatshare.storageValue)

        // Act
        val viewModel = HomeEditViewModel(repository, SelectedHome(), homeId = id)
        val state = viewModel.uiState.first { !it.isLoading }

        // Assert
        assertEquals(HomeDetails(id = id, name = "Flat 4B", shape = HomeShape.Flatshare.storageValue), state.home)
    }

    @Test
    fun savingAnExistingHomeUpdatesItInPlace() = runTest {
        // Arrange
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = homeRepository(openInMemoryDatabase())
        val id = repository.create("Flat 4B", HomeShape.Flatshare.storageValue)
        val viewModel = HomeEditViewModel(repository, SelectedHome(), homeId = id)

        // Act
        viewModel.save(HomeDetails(id = id, name = "Flat 4C", shape = HomeShape.Other.storageValue))
        viewModel.uiState.first { it.isFinished }

        // Assert
        assertEquals(HomeDetails(id = id, name = "Flat 4C", shape = HomeShape.Other.storageValue), repository.get(id))
        assertEquals(1, repository.homes.first().size)
    }

    @Test
    fun deletingRemovesTheHomeAndClearsItFromTheSelection() = runTest {
        // Arrange
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = homeRepository(openInMemoryDatabase())
        val id = repository.create("Flat 4B", HomeShape.Flatshare.storageValue)
        val selectedHome = SelectedHome().apply { select(id) }
        val viewModel = HomeEditViewModel(repository, selectedHome, homeId = id)

        // Act
        viewModel.delete(id)
        val state = viewModel.uiState.first { it.isFinished }

        // Assert
        assertNull(repository.get(id))
        assertNull(selectedHome.homeId.value)
        assertTrue(state.isFinished)
    }
}
