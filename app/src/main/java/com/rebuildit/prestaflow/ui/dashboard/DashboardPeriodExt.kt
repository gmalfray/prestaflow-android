package com.rebuildit.prestaflow.ui.dashboard

import androidx.annotation.StringRes
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardPeriod
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/** Retourne le string resource correspondant au libellé d'une [DashboardPeriod]. */
@StringRes
fun DashboardPeriod.labelRes(): Int =
    when (this) {
        DashboardPeriod.TODAY -> R.string.dashboard_period_today
        DashboardPeriod.WEEK -> R.string.dashboard_period_week
        DashboardPeriod.MONTH -> R.string.dashboard_period_month
        DashboardPeriod.QUARTER -> R.string.dashboard_period_quarter
        DashboardPeriod.YEAR -> R.string.dashboard_period_year
    }

/**
 * Borne `created_from` (yyyy-MM-dd) du drill-down "Nouveaux clients", alignée sur la période
 * civile affichée par le dashboard — mêmes bornes que le connecteur
 * (`DashboardService::resolvePeriodRange` v1.15.0) : aujourd'hui / lundi de la semaine en cours
 * (ISO) / 1ᵉʳ du mois en cours / 1ᵉʳ jour du trimestre civil en cours / 1ᵉʳ janvier. En plage
 * libre, la date de début choisie. Toutes les périodes se terminant « maintenant », `created_from`
 * seul suffit à faire correspondre la liste Clients au compteur du KPI (pas besoin de `created_to`).
 */
internal fun newCustomersCreatedFrom(
    period: DashboardPeriod,
    customRange: Pair<String, String>?,
    today: LocalDate = LocalDate.now(),
): String {
    customRange?.let { return it.first }
    val from =
        when (period) {
            DashboardPeriod.TODAY -> today
            DashboardPeriod.WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            DashboardPeriod.MONTH -> today.withDayOfMonth(1)
            DashboardPeriod.QUARTER -> today.withMonth((today.monthValue - 1) / 3 * 3 + 1).withDayOfMonth(1)
            DashboardPeriod.YEAR -> today.withDayOfYear(1)
        }
    return from.format(DateTimeFormatter.ISO_LOCAL_DATE)
}

/**
 * Bornes `date_from` / `date_to` (`yyyy-MM-dd HH:mm:ss`, fin de journée incluse) de la période
 * affichée, pour `GET reports/bestsellers` : le connecteur compare `date_add <= date_to`, donc une
 * date seule exclurait toute la dernière journée. Plage libre : ses dates ; preset : du début de
 * période civile à aujourd'hui.
 */
internal fun periodDateRange(
    period: DashboardPeriod,
    customRange: Pair<String, String>?,
    today: LocalDate = LocalDate.now(),
): Pair<String, String> {
    val from = newCustomersCreatedFrom(period, customRange, today)
    val to = customRange?.second ?: today.format(DateTimeFormatter.ISO_LOCAL_DATE)
    return "$from 00:00:00" to "$to 23:59:59"
}
