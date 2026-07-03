package com.rebuildit.prestaflow.fakes

import com.rebuildit.prestaflow.data.auth.LoginApiClientContract
import com.rebuildit.prestaflow.data.remote.dto.AuthRequestDto
import com.rebuildit.prestaflow.data.remote.dto.AuthResponseDto
import okhttp3.HttpUrl

/**
 * Fake de [LoginApiClientContract] pour les tests unitaires (AuthRepositoryImpl, SyncTaskExecutor).
 *
 * [loginBlock] est configurable pour simuler un succès, un échec, ou observer l'URL/la boutique
 * ciblées par chaque appel (sans jamais faire de requête réseau réelle).
 */
class FakeLoginApiClient(
    private val loginBlock: (HttpUrl, AuthRequestDto) -> AuthResponseDto = { _, _ ->
        AuthResponseDto(token = "jwt-valide", expiresIn = 3600L, scopes = emptyList())
    },
) : LoginApiClientContract {
    data class LoginCall(val apiBaseUrl: HttpUrl, val request: AuthRequestDto)

    val calls = mutableListOf<LoginCall>()

    override suspend fun login(
        apiBaseUrl: HttpUrl,
        request: AuthRequestDto,
    ): AuthResponseDto {
        calls += LoginCall(apiBaseUrl, request)
        return loginBlock(apiBaseUrl, request)
    }
}
