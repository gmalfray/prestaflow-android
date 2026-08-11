package com.rebuildit.prestaflow.ui.clients

import com.rebuildit.prestaflow.domain.capabilities.model.ShopCapabilities
import com.rebuildit.prestaflow.fakes.FakeCapabilitiesRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [ClientsTabsViewModel] n'est qu'un passe-plat vers [com.rebuildit.prestaflow.domain.capabilities.CapabilitiesRepository]. */
class ClientsTabsViewModelTest {
    @Test
    fun `expose la valeur courante du CapabilitiesRepository`() {
        val fakeRepo = FakeCapabilitiesRepository(initial = ShopCapabilities(reviews = true))

        val viewModel = ClientsTabsViewModel(fakeRepo)

        assertTrue(viewModel.capabilities.value.reviews)
    }

    @Test
    fun `reflete les emissions ulterieures du repository`() {
        val fakeRepo = FakeCapabilitiesRepository(initial = ShopCapabilities(reviews = false))
        val viewModel = ClientsTabsViewModel(fakeRepo)
        assertFalse(viewModel.capabilities.value.reviews)

        fakeRepo.emit(ShopCapabilities(reviews = true))

        assertEquals(fakeRepo.capabilities.value, viewModel.capabilities.value)
        assertTrue(viewModel.capabilities.value.reviews)
    }
}
