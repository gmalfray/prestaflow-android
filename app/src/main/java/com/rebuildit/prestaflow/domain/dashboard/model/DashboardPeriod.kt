package com.rebuildit.prestaflow.domain.dashboard.model

enum class DashboardPeriod(val queryValue: String) {
    TODAY("today"),
    WEEK("week"),
    MONTH("month"),
    QUARTER("quarter"),
    YEAR("year")
}
