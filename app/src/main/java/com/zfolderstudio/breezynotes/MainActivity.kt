package com.zfolderstudio.breezynotes

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Share
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zfolderstudio.breezynotes.ui.theme.MyApplicationTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Note(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

class NotesRepository(private val context: Context) {
    private val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "BreezyNotes")

    init {
        if (!directory.exists()) {
            directory.mkdirs()
        }
    }

    fun getNotes(): List<Note> {
        val list = mutableListOf<Note>()
        if (!directory.exists()) return list

        val files = directory.listFiles { file -> file.extension == "txt" } ?: return list

        for (file in files) {
            val content = try { file.readText() } catch (e: Exception) { "" }
            list.add(Note(
                id = file.name,
                title = file.nameWithoutExtension,
                content = content,
                timestamp = file.lastModified()
            ))
        }
        return list.sortedByDescending { it.timestamp }
    }

    fun saveNote(note: Note, oldId: String?, showToast: Boolean = false): Note {
        if (!directory.exists()) {
            directory.mkdirs()
        }

        var filename = "${note.title}.txt"

        if (oldId != null && oldId != filename) {
            val oldFile = File(directory, oldId)
            if (oldFile.exists()) {
                oldFile.delete()
            }
        }

        var file = File(directory, filename)
        var counter = 1

        if (oldId != filename) {
            while (file.exists()) {
                filename = "${note.title} ($counter).txt"
                file = File(directory, filename)
                counter++
            }
        }

        try {
            file.writeText(note.content)
            android.media.MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
            if (showToast) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(context, "Saved to Documents/BreezyNotes", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (showToast) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(context, "Failed to save note", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }

        return note.copy(id = filename, title = file.nameWithoutExtension, timestamp = file.lastModified())
    }

    fun deleteNote(id: String) {
        val file = File(directory, id)
        if (file.exists()) {
            file.delete()
        }
    }
}

enum class Screen { List, Edit }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = NotesRepository(this)

        setContent {
            MyApplicationTheme {
                MainScreenWithPermissions(repo)
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreenWithPermissions(repo: NotesRepository) {
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    )

    if (permissionsState.allPermissionsGranted || android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {

        NotesApp(repo)
    } else {
        Scaffold { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Storage permissions are required to save your notes as text files in the Documents folder.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { permissionsState.launchMultiplePermissionRequest() }) {
                    Text("Grant Permissions")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesApp(repo: NotesRepository) {
    var notes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var currentScreen by remember { mutableStateOf(Screen.List) }
    var editingNote by remember { mutableStateOf<Note?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var editContent by remember { mutableStateOf("") }
    var showExportMenu by remember { mutableStateOf(false) }
    var pendingExportFormat by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val format = pendingExportFormat ?: return@let
                val title = editTitle.trim().takeIf { it.isNotEmpty() } ?: "Untitled"
                val content = editContent.trim()
                coroutineScope.launch {
                    try {
                        ExportHelper.exportNote(context, uri, title, content, format)
                        android.widget.Toast.makeText(context, "Exported successfully", android.widget.Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        android.widget.Toast.makeText(context, "Export failed", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        pendingExportFormat = null
    }

    fun triggerExport(format: String, mimeType: String, extension: String) {
        pendingExportFormat = format
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            putExtra(Intent.EXTRA_TITLE, "${editTitle.trim().takeIf { it.isNotEmpty() } ?: "Untitled"}.$extension")
        }
        exportLauncher.launch(intent)
        showExportMenu = false
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val loaded = repo.getNotes()
            withContext(Dispatchers.Main) {
                notes = loaded
            }
        }
    }

    fun saveNote(navigateBack: Boolean = true) {
        val title = editTitle.trim().takeIf { it.isNotEmpty() } ?: "Untitled"
        val content = editContent.trim()

        if (title == "Untitled" && content.isEmpty() && editingNote == null) {
            if (navigateBack) currentScreen = Screen.List
            return
        }

        val noteToSave = Note(
            id = editingNote?.id ?: "",
            title = title,
            content = content,
            timestamp = System.currentTimeMillis()
        )

        val tempId = editingNote?.id
        if (navigateBack) currentScreen = Screen.List

        coroutineScope.launch(Dispatchers.IO) {
            val savedNote = repo.saveNote(noteToSave, tempId, showToast = navigateBack)
            val updatedNotes = repo.getNotes()
            withContext(Dispatchers.Main) {
                editingNote = savedNote
                notes = updatedNotes
            }
        }
    }

    fun deleteNote(id: String) {
        if (currentScreen == Screen.Edit && editingNote?.id == id) {
            currentScreen = Screen.List
        }
        coroutineScope.launch(Dispatchers.IO) {
            repo.deleteNote(id)
            val updatedNotes = repo.getNotes()
            withContext(Dispatchers.Main) {
                notes = updatedNotes
            }
        }
    }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            if (targetState == Screen.Edit) {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300)) togetherWith
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300))
            } else {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300)) togetherWith
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300))
            }
        },
        label = "ScreenTransition"
    ) { targetScreen ->
        if (targetScreen == Screen.List) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Notes") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = {
                    editingNote = null
                    editTitle = ""
                    editContent = ""
                    currentScreen = Screen.Edit
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Note")
                }
            }
        ) { padding ->
            if (notes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("No notes yet. Tap + to add.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(notes, key = { it.id }) { note ->
                        Card(
                            onClick = {
                                editingNote = note
                                editTitle = note.title
                                editContent = note.content
                                currentScreen = Screen.Edit
                            },
                            modifier = Modifier.fillMaxWidth().animateItem(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                if (note.title.isNotEmpty()) {
                                    Text(text = note.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (note.content.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = note.content, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(note.timestamp)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {

        LaunchedEffect(editTitle, editContent) {
            kotlinx.coroutines.delay(1000)
            if (editTitle.isNotEmpty() || editContent.isNotEmpty()) {
                saveNote(navigateBack = false)
            }
        }

        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE || event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                    saveNote(navigateBack = false)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (editingNote == null) "New Note" else "Edit Note") },
                    navigationIcon = {
                        IconButton(onClick = { saveNote() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Save and go back")
                        }
                    },
                    actions = {
                        if (editingNote != null) {
                            Box {
                                IconButton(onClick = { showExportMenu = true }) {
                                    Icon(Icons.Default.IosShare, contentDescription = "Export note")
                                }
                                DropdownMenu(
                                    expanded = showExportMenu,
                                    onDismissRequest = { showExportMenu = false }
                                ) {
                                    DropdownMenuItem(text = { Text("Plain Text (.txt)") }, onClick = { triggerExport("txt", "text/plain", "txt") })
                                    DropdownMenuItem(text = { Text("Markdown (.md)") }, onClick = { triggerExport("md", "text/markdown", "md") })
                                    DropdownMenuItem(text = { Text("HTML (.html)") }, onClick = { triggerExport("html", "text/html", "html") })
                                    DropdownMenuItem(text = { Text("PDF Document (.pdf)") }, onClick = { triggerExport("pdf", "application/pdf", "pdf") })
                                }
                            }
                            IconButton(onClick = { deleteNote(editingNote!!.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete note")
                            }
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp)
                ) {
                    if (editingNote != null) {
                        Text(
                            text = "Last edited ${SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(editingNote!!.timestamp))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                
                BasicTextField(
                    value = editTitle,
                    onValueChange = { editTitle = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                    decorationBox = { innerTextField ->
                        if (editTitle.isEmpty()) {
                            Text("Title", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        }
                        innerTextField()
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
                BasicTextField(
                    value = editContent,
                    onValueChange = { editContent = it },
                    modifier = Modifier.fillMaxSize(),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                    decorationBox = { innerTextField ->
                        if (editContent.isEmpty()) {
                            Text("Note content...", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        }
                        innerTextField()
                    }
                )
            }
        }
    }
}
}
}
