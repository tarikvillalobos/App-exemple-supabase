package com.example.appsupabase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class NotesViewModel(
    private val client: HttpClient,
    private val supabaseUrl: String,
    private val supabaseKey: String
) : ViewModel() {

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes

    init {
        loadNotes()
        observeNotesRealtime()
    }

    private fun loadNotes() {
        viewModelScope.launch {
            try {
                val response: HttpResponse = client.get("$supabaseUrl/rest/v1/notes") {
                    header("apikey", supabaseKey)
                    header("Authorization", "Bearer $supabaseKey")
                    header("Accept", "application/json")
                }
                if (response.status == HttpStatusCode.OK) {
                    val notesList: List<Note> = response.body()
                    _notes.value = notesList
                } else {
                    // Trate erros aqui
                    println("Erro ao carregar notas: ${response.status}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addNote(content: String) {
        viewModelScope.launch {
            try {
                val response: HttpResponse = client.post("$supabaseUrl/rest/v1/notes") {
                    header("apikey", supabaseKey)
                    header("Authorization", "Bearer $supabaseKey")
                    header("Content-Type", "application/json")
                    header("Prefer", "return=representation")
                    setBody(mapOf("content" to content))
                }
                if (response.status == HttpStatusCode.Created) {
                    val newNote: Note = response.body()  // A nova nota já virá com created_at e updated_at do Supabase
                    _notes.value = _notes.value + newNote
                } else {
                    println("Erro ao adicionar nota: ${response.status}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateNote(noteId: String, newContent: String) {
        viewModelScope.launch {
            try {
                val response: HttpResponse = client.patch("$supabaseUrl/rest/v1/notes?id=eq.$noteId") {
                    header("apikey", supabaseKey)
                    header("Authorization", "Bearer $supabaseKey")
                    header("Content-Type", "application/json")
                    header("Prefer", "return=representation")
                    setBody(mapOf("content" to newContent))
                }
                if (response.status == HttpStatusCode.OK) {
                    val updatedNotes: List<Note> = response.body()
                    val updatedNote = updatedNotes.firstOrNull()
                    updatedNote?.let {
                        _notes.value = _notes.value.map { note ->
                            if (note.id == it.id) it else note
                        }
                    }
                } else {
                    println("Erro ao atualizar nota: ${response.status}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteNote(noteId: String) {  // Note que mudei para String caso o id seja UUID
        viewModelScope.launch {
            try {
                val response: HttpResponse = client.delete("$supabaseUrl/rest/v1/notes?id=eq.$noteId") {
                    header("apikey", supabaseKey)
                    header("Authorization", "Bearer $supabaseKey")
                    header("Prefer", "return=representation")
                }
                if (response.status == HttpStatusCode.NoContent) {
                    _notes.value = _notes.value.filterNot { it.id == noteId }
                } else {
                    println("Erro ao deletar nota: ${response.status}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun observeNotesRealtime() {
        viewModelScope.launch {
            val websocketUrl = "$supabaseUrl/realtime/v1/websocket?apikey=$supabaseKey&vsn=1.0.0"
                .replace("https://", "wss://")

            try {
                client.webSocket(urlString = websocketUrl) {
                    // Junta-se ao canal
                    val joinMessage = mapOf(
                        "topic" to "realtime:public:notes",
                        "event" to "phx_join",
                        "payload" to emptyMap<String, Any>(),
                        "ref" to "1"
                    )
                    sendSerialized(joinMessage)

                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            handleRealtimeMessage(text)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun handleRealtimeMessage(message: String) {
        try {
            val json = Json.parseToJsonElement(message).jsonObject
            val payload = json["payload"]?.jsonObject ?: return
            val type = payload["type"]?.jsonPrimitive?.content

            when (type) {
                "INSERT" -> {
                    val newRecord = payload["record"]?.let { Json.decodeFromJsonElement<Note>(it) }
                    newRecord?.let {
                        _notes.value = _notes.value + it
                    }
                }
                "UPDATE" -> {
                    val updatedRecord = payload["record"]?.let { Json.decodeFromJsonElement<Note>(it) }
                    updatedRecord?.let {
                        _notes.value = _notes.value.map { note ->
                            if (note.id == it.id) it else note
                        }
                    }
                }
                "DELETE" -> {
                    val oldRecord = payload["old_record"]?.jsonObject
                    val deletedId = oldRecord?.get("id")?.jsonPrimitive?.content
                    deletedId?.let {
                        _notes.value = _notes.value.filterNot { note -> note.id == it }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class NotesViewModelFactory(
    private val client: HttpClient,
    private val supabaseUrl: String,
    private val supabaseKey: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NotesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NotesViewModel(client, supabaseUrl, supabaseKey) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
