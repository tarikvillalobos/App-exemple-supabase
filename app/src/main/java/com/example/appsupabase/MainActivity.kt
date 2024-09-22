package com.example.appsupabase

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.appsupabase.ui.theme.AppSupabaseTheme
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel

// Imports do Ktor
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.*

// Import para Json
import kotlinx.serialization.json.Json

class MainActivity : ComponentActivity() {

    companion object {
        private const val SUPABASE_URL = "url"
        private const val SUPABASE_ANON_KEY = "key"
    }

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
        // Instalar o plugin de WebSocket
        install(WebSockets)  // Adicione essa linha
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppSupabaseTheme {
                val viewModel: NotesViewModel = viewModel(
                    factory = NotesViewModelFactory(
                        httpClient,
                        SUPABASE_URL,
                        SUPABASE_ANON_KEY
                    )
                )
                NotesScreen(viewModel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        httpClient.close()
    }
}
