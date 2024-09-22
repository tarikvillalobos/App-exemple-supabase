package com.example.appsupabase

import kotlinx.serialization.Serializable

@Serializable
data class Note(
    val id: String,  // Se o id for um UUID, use String. Se for Integer, use Int.
    val content: String,
    val created_at: String?,  // O Supabase geralmente retorna a data no formato ISO 8601 (String).
    val updated_at: String?   // Mesma lógica para updated_at.
)
