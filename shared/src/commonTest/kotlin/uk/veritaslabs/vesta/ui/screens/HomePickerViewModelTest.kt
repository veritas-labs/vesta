package uk.veritaslabs.vesta.ui.screens

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import uk.veritaslabs.vesta.home.SelectedHome

@OptIn(ExperimentalCoroutinesApi::class)
class HomePickerViewModelTest {

    private val johnsons = HomeListItem(id = "the-johnsons", name = "The Johnsons")

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun exposesTheHomesOnceLoaded() = runTest {
        // Arrange
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val viewModel = HomePickerViewModel(flowOf(listOf(johnsons)), SelectedHome())

        // Act
        val state = viewModel.uiState.first { !it.isLoading }

        // Assert
        assertEquals(listOf(johnsons), state.homes)
    }

    @Test
    fun selectingAHomeRecordsItAsTheSelectedHome() = runTest {
        // Arrange
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val selectedHome = SelectedHome()
        val viewModel = HomePickerViewModel(flowOf(listOf(johnsons)), selectedHome)

        // Act
        viewModel.selectHome(johnsons)

        // Assert
        assertEquals("the-johnsons", selectedHome.homeId.value)
    }
}
