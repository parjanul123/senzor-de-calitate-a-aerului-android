package com.example.senzordeaer

import kotlinx.serialization.Serializable

@Serializable
data class WebLoginRequest(
    val id: String,
    val token: String,
    val status: String,
    val user_id: String? = null,
    val created_at: String,
    val expires_at: String,
    val approved_at: String? = null
)