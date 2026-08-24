package com.example.senzordeaer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Gestionează robust ciclul de viață al token-ului de acces: verifică expirarea
 * și reîmprospătează automat folosind refresh_token-ul, cu protecție împotriva
 * reîmprospătărilor concurente (mai multe cereri simultane declanșează un singur refresh).
 */
class TokenManager(
    private val sessionManager: SessionManager,
    private val authClient: SupabaseAuthClient = SupabaseAuthClient()
) {
    private val refreshMutex = Mutex()

    /**
     * Returnează un access token valid, reîmprospătându-l în prealabil dacă a expirat
     * sau este aproape de expirare. Întoarce null dacă nu există sesiune sau reîmprospătarea eșuează.
     */
    suspend fun getValidAccessToken(): String? = withContext(Dispatchers.IO) {
        val currentToken = sessionManager.accessToken ?: return@withContext null
        if (!sessionManager.isAccessTokenExpired) return@withContext currentToken

        refreshMutex.withLock {
            // Re-verificăm după obținerea lock-ului: alt apel poate fi reîmprospătat deja sesiunea.
            val refreshedInMeantime = sessionManager.accessToken
            if (refreshedInMeantime != null && !sessionManager.isAccessTokenExpired) {
                return@withLock refreshedInMeantime
            }

            val refreshToken = sessionManager.refreshToken ?: return@withLock null
            val userId = sessionManager.userId ?: return@withLock null
            try {
                val response = authClient.refreshSession(refreshToken)
                val newAccessToken = response.get("access_token")?.asString ?: return@withLock null
                val newRefreshToken = response.get("refresh_token")?.asString ?: refreshToken
                val expiresIn = response.get("expires_in")?.asLong ?: 3600L
                sessionManager.saveSession(newAccessToken, newRefreshToken, userId, expiresIn)
                newAccessToken
            } catch (e: Exception) {
                null
            }
        }
    }
}
