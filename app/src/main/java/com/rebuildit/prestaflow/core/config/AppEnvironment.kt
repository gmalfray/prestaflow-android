package com.rebuildit.prestaflow.core.config

import com.rebuildit.prestaflow.BuildConfig

data class AppEnvironment(
    val name: EnvironmentName,
    val apiBaseUrl: String
) {
    enum class EnvironmentName {
        PREPROD,
        PROD
    }

    companion object {
        fun fromBuildConfig(): AppEnvironment = AppEnvironment(
            name = EnvironmentName.valueOf(BuildConfig.ENVIRONMENT_NAME),
            apiBaseUrl = BuildConfig.API_BASE_URL
        )
    }
}
