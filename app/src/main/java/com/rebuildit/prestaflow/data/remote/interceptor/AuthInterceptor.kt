package com.rebuildit.prestaflow.data.remote.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor
    @Inject
    constructor(
        private val tokenProvider: TokenProvider,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val token = tokenProvider.getAccessToken()
            val request =
                if (token.isNullOrBlank()) {
                    chain.request()
                } else {
                    chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                }
            return chain.proceed(request)
        }

        interface TokenProvider {
            fun getAccessToken(): String?
        }
    }
