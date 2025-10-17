package com.rebuildit.prestaflow.core.network

import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.core.ui.UiText
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.HttpException

@Singleton
class NetworkErrorMapper @Inject constructor() {

    fun map(throwable: Throwable): UiText = when (throwable) {
        is IOException -> UiText.FromResources(R.string.error_network_unreachable)
        is HttpException -> when (throwable.code()) {
            401 -> UiText.FromResources(R.string.error_unauthorized)
            403 -> UiText.FromResources(R.string.error_forbidden)
            else -> UiText.FromResources(R.string.error_generic)
        }
        else -> UiText.FromResources(R.string.error_generic)
    }
}
