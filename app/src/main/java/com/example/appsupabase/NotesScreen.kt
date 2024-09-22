package com.example.appsupabase

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(viewModel: NotesViewModel) {
    val notes by viewModel.notes.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(text = "Notas") },
            actions = {
                IconButton(onClick = { viewModel.loadNotes() }) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Atualizar")
                }
            }
        )

        if (notes.isEmpty()) {
            Text(
                text = "Nenhuma nota encontrada.",
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(notes) { note ->
                    NoteItem(note, onUpdate = { newContent ->
                        viewModel.updateNote(note.id, newContent)
                    }, onDelete = {
                        viewModel.deleteNote(note.id)
                    })
                }
            }
        }

        NoteInput(onAddNote = { content ->
            viewModel.addNote(content)
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteItem(note: Note, onUpdate: (String) -> Unit, onDelete: () -> Unit) {
    var isEditing by remember { mutableStateOf(false) }
    var content by remember { mutableStateOf(note.content) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        if (isEditing) {
            TextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                onUpdate(content)
                isEditing = false
            }) {
                Icon(imageVector = Icons.Default.Check, contentDescription = "Salvar")
            }
        } else {
            Text(
                text = note.content,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { isEditing = true }) {
                Icon(imageVector = Icons.Default.Edit, contentDescription = "Editar")
            }
            IconButton(onClick = onDelete) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Deletar")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteInput(onAddNote: (String) -> Unit) {
    var content by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = content,
            onValueChange = { content = it },
            modifier = Modifier.weight(1f),
            label = { Text("Nova Nota") }
        )
        IconButton(onClick = {
            if (content.isNotBlank()) {
                onAddNote(content)
                content = ""
            }
        }) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Adicionar")
        }
    }
}
