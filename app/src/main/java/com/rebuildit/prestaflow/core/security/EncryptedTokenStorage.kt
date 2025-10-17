package com.rebuildit.prestaflow.core.security

import android.content.SharedPreferences
import androidx.core.content.edit
import com.rebuildit.prestaflow.domain.auth.model.AuthToken
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptedTokenStorage @Inject constructor(
    private val sharedPreferences: SharedPreferences
) : TokenStorage {

    override fun persist(token: AuthToken) {
        sharedPreferences.edit {
            putString(KEY_TOKEN_VALUE, token.value)
            if (token.expiresAtEpochMillis != null) {
                putLong(KEY_TOKEN_EXPIRY, token.expiresAtEpochMillis)
            } else {
                remove(KEY_TOKEN_EXPIRY)
            }
            putStringSet(KEY_TOKEN_SCOPES, token.scopes.toSet())
        }
    }

    override fun read(): AuthToken? {
        val value = sharedPreferences.getString(KEY_TOKEN_VALUE, null) ?: return null
        val expiry = if (sharedPreferences.contains(KEY_TOKEN_EXPIRY)) {
            sharedPreferences.getLong(KEY_TOKEN_EXPIRY, 0L)
        } else null
        val scopes = sharedPreferences.getStringSet(KEY_TOKEN_SCOPES, emptySet())?.toList().orEmpty()
        return AuthToken(value = value, expiresAtEpochMillis = expiry, scopes = scopes)
    }

    override fun clear() {
        sharedPreferences.edit { clear() }
    }

    private companion object {
        const val KEY_TOKEN_VALUE = "token_value"
        const val KEY_TOKEN_EXPIRY = "token_expiry"
        const val KEY_TOKEN_SCOPES = "token_scopes"
    }
}
