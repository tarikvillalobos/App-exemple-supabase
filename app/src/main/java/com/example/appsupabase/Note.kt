package com.example.appsupabase

import kotlinx.serialization.Serializable

@Serializable
data class Note(
    val id: String,  // Se o id for UUID
    val content: String,
    val created_at: String? = null,
    val updated_at: String? = null
)
