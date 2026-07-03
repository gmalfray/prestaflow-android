package com.rebuildit.prestaflow.data.auth

import com.rebuildit.prestaflow.data.remote.dto.AuthRequestDto
import com.rebuildit.prestaflow.data.remote.dto.AuthResponseDto
import com.rebuildit.prestaflow.data.remote.interceptor.DefaultHeadersInterceptor
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sous-ensemble de [com.rebuildit.prestaflow.data.remote.api.PrestaFlowApi] utilisé par
 * [LoginApiClient] pour construire un proxy Retrofit dédié, pointé explicitement sur la
 * boutique candidate — jamais sur la boutique active.
 */
internal interface LoginOnlyApi {
    @POST("connector/login")
    suspend fun login(
        @Body request: AuthRequestDto,
    ): AuthResponseDto
}

/**
 * Exécute l'appel de login **hors du client OkHttp partagé** (client dédié, comme
 * [com.rebuildit.prestaflow.core.notifications.ShopDeviceRegistrar]).
 *
 * Le client partagé porte [com.rebuildit.prestaflow.data.remote.interceptor.DynamicBaseUrlInterceptor]
 * et [com.rebuildit.prestaflow.data.remote.interceptor.AuthInterceptor], qui routent TOUTE requête
 * qui l'emprunte vers la boutique **active** avec son Bearer — pendant la fenêtre de login d'une
 * 2ᵉ boutique (ex. après un scan QR d'une URL non maîtrisée), une requête concurrente sur ce client
 * partagé pourrait donc faire fuiter le JWT de la boutique déjà active vers l'hôte candidat.
 *
 * En passant par un `Retrofit`/`OkHttpClient` construits ici, à la volée, pointés explicitement
 * sur [apiBaseUrl], ce risque est structurellement exclu : ce client n'a ni intercepteur de
 * routage dynamique, ni intercepteur d'authentification. Le routage global
 * ([com.rebuildit.prestaflow.core.network.ApiEndpointManager.setActiveBaseUrl]) n'est basculé
 * qu'APRÈS un login réussi, dans `AuthRepositoryImpl.activate`.
 */
@Singleton
class LoginApiClient
    @Inject
    constructor(
        private val json: Json,
    ) : LoginApiClientContract {
        private val httpClient: OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .readTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .writeTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .addInterceptor(DefaultHeadersInterceptor())
                .build()

        override suspend fun login(
            apiBaseUrl: HttpUrl,
            request: AuthRequestDto,
        ): AuthResponseDto =
            Retrofit.Builder()
                .baseUrl(apiBaseUrl)
                .client(httpClient)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(LoginOnlyApi::class.java)
                .login(request)

        private companion object {
            const val TIMEOUT_SECONDS = 30L
        }
    }
