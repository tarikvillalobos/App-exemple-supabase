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

    fun loadNotes() {
        viewModelScope.launch {
            try {
                val response: HttpResponse = client.get("$supabaseUrl/rest/v1/newnotes") {
                    header("apikey", supabaseKey)
                    header("Authorization", "Bearer $supabaseKey")
                    header("Accept", "application/json")
                }

                if (response.status == HttpStatusCode.OK) {
                    val notesList: List<Note> = response.body()  // Aqui está esperando que a resposta seja uma lista de "Note"
                    _notes.value = notesList
                } else {
                    println("Erro ao carregar notas: ${response.status}")
                    println(response.bodyAsText())  // Verifique a resposta do servidor
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addNote(content: String) {
        viewModelScope.launch {
            try {
                val response: HttpResponse = client.post("$supabaseUrl/rest/v1/newnotes") {
                    header("apikey", supabaseKey)
                    header("Authorization", "Bearer $supabaseKey")
                    header("Content-Type", "application/json")
                    header("Prefer", "return=representation")
                    setBody(mapOf("content" to content))
                }

                if (response.status == HttpStatusCode.Created) {
                    loadNotes()  // Atualizar a tela após inserir
                    val newNote: Note = response.body()
                    _notes.value = _notes.value + newNote
                } else {
                    println("Erro ao adicionar nota: ${response.status}")
                    println(response.bodyAsText())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateNote(noteId: String, newContent: String) {
        viewModelScope.launch {
            try {
                val response: HttpResponse = client.patch("$supabaseUrl/rest/v1/newnotes?id=eq.$noteId") {
                    header("apikey", supabaseKey)
                    header("Authorization", "Bearer $supabaseKey")
                    header("Content-Type", "application/json")
                    header("Prefer", "return=representation")
                    setBody(mapOf("content" to newContent))
                }
                if (response.status == HttpStatusCode.OK) {
                    loadNotes()  // Atualizar a tela após o update
                } else {
                    println("Erro ao atualizar nota: ${response.status}")
                    println(response.bodyAsText())  // Verificar o erro retornado pelo servidor
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            try {
                // Deletar nota do Supabase
                val response: HttpResponse = client.delete("$supabaseUrl/rest/v1/newnotes?id=eq.$noteId") {
                    header("apikey", supabaseKey)
                    header("Authorization", "Bearer $supabaseKey")
                    header("Prefer", "return=representation")
                }

                if (response.status == HttpStatusCode.NoContent || response.status == HttpStatusCode.OK) {
                    // Se a nota foi deletada com sucesso, recarregue as notas
                    loadNotes()  // Atualizar a lista de notas
                } else {
                    println("Erro ao deletar nota: ${response.status}")
                    println("Resposta do servidor: ${response.bodyAsText()}")  // Log do erro do servidor
                }
            } catch (e: Exception) {
                e.printStackTrace()  // Exibir erro no console
            }
        }
    }

    private fun observeNotesRealtime() {
        viewModelScope.launch {
            val websocketUrl = "$supabaseUrl/realtime/v1/websocket?apikey=$supabaseKey&vsn=1.0.0"
                .replace("https://", "wss://")

            try {
                client.webSocket(urlString = websocketUrl) {
                    println("Conectado ao WebSocket para atualizações em tempo real")

                    // Inscreva-se ao canal da tabela 'newnotes'
                    val joinMessage = mapOf(
                        "topic" to "realtime:public:newnotes",
                        "event" to "phx_join",
                        "payload" to emptyMap<String, Any>(),
                        "ref" to "1"
                    )
                    sendSerialized(joinMessage)

                    // Recebendo atualizações em tempo real
                    // TODO Essa parte ainda não está funcionando
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            println("Mensagem WebSocket recebida: $text")

                            // Processa as mensagens recebidas
                            handleRealtimeMessage(text)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                println("Erro ao conectar ao WebSocket: ${e.message}")
            }
        }
    }

    private fun handleRealtimeMessage(message: String) {
        try {
            // Log para ver as mensagens recebidas
            println("Mensagem recebida via WebSocket: $message")

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
