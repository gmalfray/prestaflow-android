package com.rebuildit.prestaflow.data.remote.interceptor

import com.rebuildit.prestaflow.core.network.ApiEndpointManager
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber

@Singleton
class DynamicBaseUrlInterceptor @Inject constructor(
    private val endpointManager: ApiEndpointManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!endpointManager.hasOverride()) {
            return chain.proceed(request)
        }

        val activeBase = endpointManager.getActiveBaseUrl()
        val originalUrl = request.url
        val relativePath = endpointManager.extractRelativePath(originalUrl).trimStart('/')

        val basePath = ensureTrailingSlash(activeBase.encodedPath)
        val newEncodedPath = basePath + relativePath

        val newUrlBuilder = activeBase.newBuilder()
            .encodedPath(if (newEncodedPath.startsWith("/")) newEncodedPath else "/$newEncodedPath")

        originalUrl.encodedQuery?.let { query ->
            newUrlBuilder.encodedQuery(query)
        }

        originalUrl.fragment?.let { fragment ->
            newUrlBuilder.fragment(fragment)
        }

        val newUrl = newUrlBuilder.build()
        Timber.d(
            "DynamicBaseUrlInterceptor: overriding %s -> %s",
            originalUrl,
            newUrl
        )
        val newRequest = request.newBuilder().url(newUrl).build()
        return chain.proceed(newRequest)
    }

    private fun ensureTrailingSlash(path: String): String {
        return when {
            path.isEmpty() -> "/"
            path.endsWith("/") -> path
            else -> "$path/"
        }
    }
}
