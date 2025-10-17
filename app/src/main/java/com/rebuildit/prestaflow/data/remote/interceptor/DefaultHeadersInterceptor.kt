package com.rebuildit.prestaflow.data.remote.interceptor

import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.Response

@Singleton
class DefaultHeadersInterceptor @Inject constructor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .build()
        return chain.proceed(request)
    }
}
