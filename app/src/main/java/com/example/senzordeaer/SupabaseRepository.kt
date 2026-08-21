package com.example.senzordeaer

import android.util.Log
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SupabaseRepository {
    private val client = SupabaseClient.client

    fun getCurrentUserId(): String? = client.auth.currentUserOrNull()?.id

    suspend fun getLoginRequest(token: String): WebLoginRequest? = withContext(Dispatchers.IO) {
        try {
            Log.d("SupabaseRepo", "Searching for token: $token")
            val response = client.from("web_login_requests")
                .select {
                    filter {
                        eq("token", token)
                    }
                }
            val request = response.decodeSingleOrNull<WebLoginRequest>()
            Log.d("SupabaseRepo", "Request found: ${request != null}, status: ${request?.status}")
            request
        } catch (e: Exception) {
            Log.e("SupabaseRepo", "Error getting login request: ${e.message}", e)
            null
        }
    }

    suspend fun updateLoginRequestStatus(id: String, status: String, userId: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d("SupabaseRepo", "Updating request $id to status: $status for user: $userId")
            
            val content = buildJsonObject {
                put("status", status)
                // Trimitem user_id doar dacă nu este null sau gol, pentru a evita erori de format UUID în Postgres
                if (!userId.isNullOrBlank()) {
                    put("user_id", userId)
                    put("approved_at", Clock.System.now().toString())
                }
            }

            client.from("web_login_requests").update(content) {
                filter {
                    eq("id", id)
                }
            }
            Log.d("SupabaseRepo", "Update successful for $id")
            true
        } catch (e: Exception) {
            Log.e("SupabaseRepo", "Error updating login request: ${e.message}", e)
            false
        }
    }
}