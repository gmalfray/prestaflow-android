package com.rebuildit.prestaflow.core.network

import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.core.ui.UiText
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.HttpException
import timber.log.Timber

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403

@Singleton
class NetworkErrorMapper @Inject constructor() {

    fun map(throwable: Throwable): UiText = when (throwable) {
        is IOException -> {
            Timber.w(throwable, "Network unreachable")
            UiText.FromResources(R.string.error_network_unreachable)
        }
        is HttpException -> {
            val statusCode = throwable.code()
            val requestUrl = throwable.response()?.raw()?.request?.url?.toString()
            Timber.w(
                throwable,
                "HTTP error %d for request %s (reason=%s)",
                statusCode,
                requestUrl ?: "n/a",
                throwable.message() ?: "n/a"
            )
            when (statusCode) {
                HTTP_UNAUTHORIZED -> UiText.FromResources(R.string.error_unauthorized)
                HTTP_FORBIDDEN -> UiText.FromResources(R.string.error_forbidden)
                else -> UiText.FromResources(
                    R.string.error_http_with_code,
                    listOf(statusCode)
                )
            }
        }
        else -> {
            Timber.e(throwable, "Unexpected network error")
            UiText.FromResources(R.string.error_generic)
        }
    }
}
