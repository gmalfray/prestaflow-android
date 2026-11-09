package com.rebuildit.prestaflow.ui.dashboard

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardWidget
import com.rebuildit.prestaflow.domain.dashboard.model.SalesByUniverse
import com.rebuildit.prestaflow.domain.dashboard.model.UniverseSales
import com.rebuildit.prestaflow.ui.theme.Dimensions
import com.rebuildit.prestaflow.ui.theme.PrestaFlowTheme
import java.text.NumberFormat
import kotlin.math.roundToInt

private const val PERCENT = 100
private val BarHeight = 8.dp
private val LegendDotSize = 10.dp
private val AmountMaxWidth = 140.dp
private val BarShape = RoundedCornerShape(4.dp)

/** Libellé FR/EN/... d'un univers (clés stables du connecteur) ; `autre` pour toute clé inconnue. */
@StringRes
internal fun universeLabelRes(key: String): Int =
    when (key) {
        "kits" -> R.string.dashboard_universe_kits
        "patrons_pdf" -> R.string.dashboard_universe_patrons_pdf
        "ebooks" -> R.string.dashboard_universe_ebooks
        "livres" -> R.string.dashboard_universe_livres
        "fils" -> R.string.dashboard_universe_fils
        "mercerie" -> R.string.dashboard_universe_mercerie
        else -> R.string.dashboard_universe_autre
    }

/**
 * Carte « Ventes par univers » : barres de part du CA (numérique en `primaryContainer`, physique en
 * `primary`), poids du numérique, puis mention des patrons gratuits. `Unavailable` est masqué en
 * amont par l'écran (comme les avis) ; ici il retombe sur le squelette par sécurité.
 */
@Composable
internal fun SalesByUniverseCard(
    state: WidgetLoad<SalesByUniverse>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currency = rememberCurrencyFormatter()
    NetworkWidgetCard(DashboardWidget.SALES_BY_UNIVERSE, state, onRetry, modifier) { data ->
        if (data.isEmpty) {
            WidgetEmptyText()
        } else {
            SalesByUniverseContent(data, currency)
        }
    }
}

@Composable
private fun SalesByUniverseContent(
    data: SalesByUniverse,
    currency: NumberFormat,
) {
    val total = data.totalTaxIncl
    Column(verticalArrangement = Arrangement.spacedBy(Dimensions.spacingM)) {
        data.universes.forEach { universe ->
            UniverseRow(universe = universe, share = (universe.caTaxIncl / total).toFloat(), currency = currency)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingS)) {
            Box(Modifier.size(LegendDotSize).background(MaterialTheme.colorScheme.primaryContainer, CircleShape))
            Text(
                text = stringResource(R.string.dashboard_universe_digital_share, (data.digitalShareRatio * PERCENT).roundToInt()),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (data.freePatternsQuantity > 0) {
            Text(
                text = stringResource(R.string.dashboard_universe_free_patterns, data.freePatternsQuantity),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UniverseRow(
    universe: UniverseSales,
    share: Float,
    currency: NumberFormat,
) {
    val barColor = if (universe.isDigital) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(Dimensions.spacingXs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
        ) {
            Text(
                text = stringResource(universeLabelRes(universe.key)),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = currency.format(universe.caTaxIncl),
                modifier = Modifier.widthIn(max = AmountMaxWidth),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ShareBar(share = share, color = barColor)
    }
}

/** Barre de proportion (piste `surfaceContainerHigh`, remplissage [color]) ; plancher visuel à 2 %. */
@Composable
internal fun ShareBar(
    share: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(BarHeight)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, BarShape),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(share.coerceIn(0.02f, 1f))
                    .background(color, BarShape),
        )
    }
}

private val PreviewUniverses =
    SalesByUniverse(
        universes =
            listOf(
                UniverseSales("kits", false, 73249.52, 2602),
                UniverseSales("patrons_pdf", true, 18420.0, 1900),
                UniverseSales("fils", false, 9210.4, 700),
                UniverseSales("ebooks", true, 640.0, 40),
            ),
        freePatternsQuantity = 899,
        digitalShareRatio = 0.188f,
    )

@Preview(name = "Univers 1.0", showBackground = true)
@Preview(name = "Univers 1.5", showBackground = true, fontScale = 1.5f, heightDp = 900)
@Preview(name = "Univers 2.0", showBackground = true, fontScale = 2f, heightDp = 1200)
@Composable
private fun PreviewSalesByUniverseCard() {
    PrestaFlowTheme {
        Column(Modifier.padding(Dimensions.spacingM), verticalArrangement = Arrangement.spacedBy(Dimensions.spacingM)) {
            SalesByUniverseCard(WidgetLoad.Data(PreviewUniverses), onRetry = {})
            SalesByUniverseCard(WidgetLoad.Error, onRetry = {})
        }
    }
}
