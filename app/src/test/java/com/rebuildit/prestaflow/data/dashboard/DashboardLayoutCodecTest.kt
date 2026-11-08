package com.rebuildit.prestaflow.data.dashboard

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardLayout
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardLayoutItem
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardLayoutCodecTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()

    private fun newStore() = PreferenceDataStoreFactory.create(scope = CoroutineScope(dispatcher)) { tmp.newFile("prefs.preferences_pb") }

    @Test
    fun `absent ou corrompu retombe sur la disposition par défaut`() {
        assertEquals(DashboardLayout.DEFAULT, DashboardLayoutCodec.decode(null))
        assertEquals(DashboardLayout.DEFAULT, DashboardLayoutCodec.decode(""))
        assertEquals(DashboardLayout.DEFAULT, DashboardLayoutCodec.decode("{pas du json"))
        assertEquals(DashboardLayout.DEFAULT, DashboardLayoutCodec.decode("""{"id":"chart"}"""))
    }

    @Test
    fun `aller-retour conserve ordre et visibilité`() {
        val layout =
            DashboardLayout(
                listOf(
                    DashboardLayoutItem(DashboardWidget.CHART, true),
                    DashboardLayoutItem(DashboardWidget.TURNOVER, false),
                    DashboardLayoutItem(DashboardWidget.LOW_STOCK, true),
                ) +
                    DashboardWidget.entries
                        .filter { it !in setOf(DashboardWidget.CHART, DashboardWidget.TURNOVER, DashboardWidget.LOW_STOCK) }
                        .map { DashboardLayoutItem(it, it.defaultEnabled) },
            )
        assertEquals(layout, DashboardLayoutCodec.decode(DashboardLayoutCodec.encode(layout)))
    }

    @Test
    fun `format persisté - tableau ordonné d'objets id et visible`() {
        val raw = DashboardLayoutCodec.encode(DashboardLayout.DEFAULT)
        assertTrue(raw.startsWith("""[{"id":"kpi_turnover","visible":true},"""))
    }

    @Test
    fun `id inconnu et id réservé phase 2 sont ignorés, doublon aussi`() {
        val raw =
            """[{"id":"latest_orders","visible":true},{"id":"chart","visible":false},
                {"id":"nope","visible":true},{"id":"chart","visible":true},{"id":"kpi_orders","visible":true}]"""
        val layout = DashboardLayoutCodec.decode(raw)
        assertEquals(listOf(DashboardWidget.CHART, DashboardWidget.ORDERS), layout.items.take(2).map { it.widget })
        assertEquals(false, layout.items[0].visible)
        assertEquals(DashboardWidget.entries.size, layout.items.size)
        assertEquals(DashboardWidget.entries.toSet(), layout.items.map { it.widget }.toSet())
    }

    @Test
    fun `widget absent de la sauvegarde ajouté à la fin selon defaultEnabled`() {
        val raw = """[{"id":"chart","visible":true},{"id":"kpi_turnover","visible":false}]"""
        val layout = DashboardLayoutCodec.decode(raw)
        val appended = layout.items.drop(2)
        assertEquals(
            DashboardWidget.entries.filter { it != DashboardWidget.CHART && it != DashboardWidget.TURNOVER },
            appended.map { it.widget },
        )
        appended.forEach { assertEquals(it.widget.defaultEnabled, it.visible) }
        assertEquals(false, layout.items[1].visible)
    }

    @Test
    fun `défaut - 6 widgets visibles dans l'ordre historique, 3 masqués`() {
        assertEquals(
            listOf(
                DashboardWidget.TURNOVER,
                DashboardWidget.ORDERS,
                DashboardWidget.AVG_CART,
                DashboardWidget.NEW_CUSTOMERS,
                DashboardWidget.FREE_CONVERSION,
                DashboardWidget.CHART,
            ),
            DashboardLayout.DEFAULT.visibleWidgets,
        )
    }

    @Test
    fun `ids de widgets uniques`() {
        assertEquals(DashboardWidget.entries.size, DashboardWidget.entries.map { it.id }.toSet().size)
    }

    @Test
    fun `rows empile les HALF deux par ligne et isole les FULL`() {
        assertEquals(
            listOf(
                listOf(DashboardWidget.TURNOVER, DashboardWidget.ORDERS),
                listOf(DashboardWidget.AVG_CART, DashboardWidget.NEW_CUSTOMERS),
                listOf(DashboardWidget.FREE_CONVERSION),
                listOf(DashboardWidget.CHART),
            ),
            DashboardLayout.DEFAULT.rows(),
        )
        val mixed =
            DashboardLayout(
                listOf(
                    DashboardLayoutItem(DashboardWidget.TURNOVER, true),
                    DashboardLayoutItem(DashboardWidget.CHART, true),
                    DashboardLayoutItem(DashboardWidget.ORDERS, true),
                    DashboardLayoutItem(DashboardWidget.AVG_CART, true),
                    DashboardLayoutItem(DashboardWidget.LOW_STOCK, true),
                    DashboardLayoutItem(DashboardWidget.ORDERS_SPLIT, false),
                ),
            )
        assertEquals(
            listOf(
                listOf(DashboardWidget.TURNOVER),
                listOf(DashboardWidget.CHART),
                listOf(DashboardWidget.ORDERS, DashboardWidget.AVG_CART),
                listOf(DashboardWidget.LOW_STOCK),
            ),
            mixed.rows(),
        )
    }

    @Test
    fun `repository - défaut, persistance, valeur corrompue`() =
        runTest {
            val store = newStore()
            val repo = DashboardPreferencesRepositoryImpl(store, dispatcher)
            assertEquals(DashboardLayout.DEFAULT, repo.widgetLayout.first())

            val custom =
                DashboardLayout(
                    DashboardLayout.DEFAULT.items.map {
                        if (it.widget == DashboardWidget.PENDING_ORDERS) it.copy(visible = true) else it
                    }.reversed(),
                )
            repo.setWidgetLayout(custom)
            assertEquals(custom, repo.widgetLayout.first())

            store.edit { it[stringPreferencesKey("dashboard_widgets_layout")] = "garbage" }
            assertEquals(DashboardLayout.DEFAULT, repo.widgetLayout.first())
        }
}
