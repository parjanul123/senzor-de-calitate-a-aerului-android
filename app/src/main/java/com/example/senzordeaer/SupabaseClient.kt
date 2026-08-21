package com.example.senzordeaer

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseClient {
    private const val SUPABASE_URL = "https://eakzxbfcwbgfxfujzote.supabase.co"
    private const val SUPABASE_KEY = "sb_publishable_ofI6pPkeb2csAsw_ZqhCng_d3ADhRZU"

    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_KEY
    ) {
        install(Auth)
        install(Postgrest)
    }
}
