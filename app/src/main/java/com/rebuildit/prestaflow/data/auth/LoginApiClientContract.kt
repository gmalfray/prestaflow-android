package com.rebuildit.prestaflow.data.auth

import com.rebuildit.prestaflow.data.remote.dto.AuthRequestDto
import com.rebuildit.prestaflow.data.remote.dto.AuthResponseDto
import okhttp3.HttpUrl

/**
 * Contrat d'exécution de l'appel de login, extrait de [LoginApiClient] pour permettre
 * l'injection de fakes en test (cf. [com.rebuildit.prestaflow.core.notifications.ShopDeviceRegistrarContract]).
 */
interface LoginApiClientContract {
    /**
     * Exécute le login directement contre [apiBaseUrl] (la boutique **candidate**, pas
     * nécessairement la boutique active). Peut lever [retrofit2.HttpException] (HTTP non-2xx)
     * ou [java.io.IOException] (réseau), comme le ferait un appel Retrofit classique — ce sont
     * les types que sait interpréter `AuthRepositoryImpl.mapLoginFailure`.
     */
    suspend fun login(
        apiBaseUrl: HttpUrl,
        request: AuthRequestDto,
    ): AuthResponseDto
}
