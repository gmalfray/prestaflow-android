package com.rebuildit.prestaflow.ui.dashboard

import androidx.annotation.StringRes
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardPeriod

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
