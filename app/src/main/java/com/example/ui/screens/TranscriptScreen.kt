package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.db.TranscriptEntity
import com.example.data.db.TranscriptLineEntity
import com.example.data.model.LiveSubtitle
import com.example.ui.AppViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptScreen(
    viewModel: AppViewModel,
    onOpenGoogleTranslateWebView: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Canlı Oturum, 1: Kayıtlı Transkriptler

    val liveSubtitles by viewModel.liveSubtitles.collectAsState()
    val isRunning by viewModel.isServiceRunning.collectAsState()
    val savedTranscripts by viewModel.transcripts.collectAsState()
    val preferences by viewModel.preferences.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedSavedTranscript by remember { mutableStateOf<TranscriptEntity?>(null) }
    val savedLines by viewModel.selectedTranscriptLines.collectAsState()

    // Export launcher
    var exportContentToSave by remember { mutableStateOf<String?>(null) }
    var exportMimeType by remember { mutableStateOf("text/plain") }

    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(exportMimeType)
    ) { uri: Uri? ->
        if (uri != null && exportContentToSave != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(exportContentToSave!!.toByteArray(StandardCharsets.UTF_8))
                }
                Toast.makeText(context, "Dosya başarıyla kaydedildi!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Kaydetme hatası: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Canlı Oturum (${liveSubtitles.size})") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Kayıtlı (${savedTranscripts.size})") }
            )
        }

        if (selectedTab == 0) {
            // Live Session
            LiveSessionView(
                liveSubtitles = liveSubtitles,
                isRunning = isRunning,
                onSaveAllToNotes = {
                    if (liveSubtitles.isNotEmpty()) {
                        val text = liveSubtitles.joinToString("\n\n") { sub ->
                            "[${sub.timeFormatted}] ${sub.turkishText.ifBlank { sub.originalText }}"
                        }
                        viewModel.addNote("Canlı Oturum Transkripti", text) {
                            Toast.makeText(context, "Tüm transkript Notlar'a kaydedildi!", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onSaveLineToNote = { sub ->
                    val text = if (sub.turkishText.isNotBlank()) "${sub.turkishText}\n(${sub.originalText})" else sub.originalText
                    viewModel.addNote("Not [${sub.timeFormatted}]", text) {
                        Toast.makeText(context, "Satır Notlar'a kaydedildi!", Toast.LENGTH_SHORT).show()
                    }
                },
                onGoogleTranslate = { text ->
                    handleGoogleTranslate(context, text, preferences.openTranslateInWebView, onOpenGoogleTranslateWebView)
                },
                onExportTxt = {
                    val fullText = liveSubtitles.joinToString("\n") { "[${it.timeFormatted}] ${it.turkishText.ifBlank { it.originalText }}" }
                    exportContentToSave = fullText
                    exportMimeType = "text/plain"
                    createDocLauncher.launch("transkript_${System.currentTimeMillis()}.txt")
                },
                onExportSrt = {
                    val srt = generateSrt(liveSubtitles.map { Pair(it.timestampMs, Pair(it.originalText, it.turkishText)) })
                    exportContentToSave = srt
                    exportMimeType = "application/x-subrip"
                    createDocLauncher.launch("altyazi_${System.currentTimeMillis()}.srt")
                },
                onShare = {
                    val fullText = liveSubtitles.joinToString("\n") { "[${it.timeFormatted}] ${it.turkishText.ifBlank { it.originalText }}" }
                    shareText(context, fullText)
                }
            )
        } else {
            // Saved Transcripts
            SavedTranscriptsView(
                transcripts = savedTranscripts.filter {
                    searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true) || it.previewText.contains(searchQuery, ignoreCase = true)
                },
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onSelectTranscript = { entity ->
                    selectedSavedTranscript = entity
                    viewModel.loadTranscriptLines(entity.id)
                },
                onDeleteTranscript = { id ->
                    viewModel.deleteTranscript(id)
                    Toast.makeText(context, "Transkript silindi", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    // Modal Sheet for viewing saved transcript lines
    if (selectedSavedTranscript != null) {
        val transcript = selectedSavedTranscript!!
        ModalBottomSheet(
            onDismissRequest = { selectedSavedTranscript = null }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(transcript.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${transcript.totalLines} Cümle • ${transcript.languageName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Row {
                        IconButton(onClick = {
                            val text = savedLines.joinToString("\n") { "[${it.timeFormatted}] ${it.turkishText.ifBlank { it.originalText }}" }
                            shareText(context, text)
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Paylaş")
                        }
                        IconButton(onClick = {
                            val text = savedLines.joinToString("\n") { "[${it.timeFormatted}] ${it.turkishText.ifBlank { it.originalText }}" }
                            exportContentToSave = text
                            exportMimeType = "text/plain"
                            createDocLauncher.launch("${transcript.title.replace(" ", "_")}.txt")
                        }) {
                            Icon(Icons.Default.Download, contentDescription = "Dışa Aktar")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(savedLines) { line ->
                        SavedLineItem(
                            line = line,
                            onSaveNote = {
                                val text = if (line.turkishText.isNotBlank()) "${line.turkishText}\n(${line.originalText})" else line.originalText
                                viewModel.addNote("Not [${line.timeFormatted}]", text) {
                                    Toast.makeText(context, "Satır Notlar'a kaydedildi!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onGoogleTranslate = { text ->
                                handleGoogleTranslate(context, text, preferences.openTranslateInWebView, onOpenGoogleTranslateWebView)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveSessionView(
    liveSubtitles: List<LiveSubtitle>,
    isRunning: Boolean,
    onSaveAllToNotes: () -> Unit,
    onSaveLineToNote: (LiveSubtitle) -> Unit,
    onGoogleTranslate: (String) -> Unit,
    onExportTxt: () -> Unit,
    onExportSrt: () -> Unit,
    onShare: () -> Unit
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new subtitles
    LaunchedEffect(liveSubtitles.size) {
        if (liveSubtitles.isNotEmpty()) {
            listState.animateScrollToItem(liveSubtitles.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isRunning) "● Canlı Yakalanıyor" else "Oturum Durduruldu",
                color = if (isRunning) Color(0xFF10B981) else Color.Gray,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )

            Row {
                IconButton(onClick = onSaveAllToNotes, enabled = liveSubtitles.isNotEmpty()) {
                    Icon(Icons.Default.BookmarkAdd, contentDescription = "Tümünü Notlara Kaydet")
                }
                IconButton(onClick = onExportTxt, enabled = liveSubtitles.isNotEmpty()) {
                    Icon(Icons.Default.Download, contentDescription = "TXT İndir")
                }
                IconButton(onClick = onExportSrt, enabled = liveSubtitles.isNotEmpty()) {
                    Icon(Icons.Default.Subtitles, contentDescription = "SRT İndir")
                }
                IconButton(onClick = onShare, enabled = liveSubtitles.isNotEmpty()) {
                    Icon(Icons.Default.Share, contentDescription = "Paylaş")
                }
            }
        }

        if (liveSubtitles.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (isRunning) "Ses bekleniyor... Henüz konuşma algılanmadı."
                    else "Alt yazı yakalama başlatıldığında konuşmalar burada listelenir.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(liveSubtitles, key = { it.id }) { item ->
                    SubtitleLineCard(
                        item = item,
                        onSaveNote = { onSaveLineToNote(item) },
                        onGoogleTranslate = {
                            val text = item.originalText.ifBlank { item.turkishText }
                            onGoogleTranslate(text)
                        }
                    )
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SubtitleLineCard(
    item: LiveSubtitle,
    onSaveNote: () -> Unit,
    onGoogleTranslate: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = onGoogleTranslate
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = item.timeFormatted.ifBlank { "00:00" },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                if (item.turkishText.isNotBlank()) {
                    Text(
                        text = item.turkishText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.originalText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = item.originalText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (item.isFinal) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onSaveNote, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.BookmarkAdd,
                    contentDescription = "Nota Ekle",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun SavedTranscriptsView(
    transcripts: List<TranscriptEntity>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelectTranscript: (TranscriptEntity) -> Unit,
    onDeleteTranscript: (Long) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Kayıtlı transkriptlerde ara...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (transcripts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Henüz kaydedilmiş transkript oturumu yok.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(transcripts, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        onClick = { onSelectTranscript(item) }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${item.totalLines} Cümle • ${item.languageName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (item.previewText.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = item.previewText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                            }

                            IconButton(onClick = { onDeleteTranscript(item.id) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Sil",
                                    tint = Color(0xFFEF4444)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SavedLineItem(
    line: TranscriptLineEntity,
    onSaveNote: () -> Unit,
    onGoogleTranslate: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = { onGoogleTranslate(line.originalText.ifBlank { line.turkishText }) }
            ),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = line.timeFormatted,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (line.turkishText.isNotBlank()) {
                    Text(line.turkishText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(line.originalText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(line.originalText, style = MaterialTheme.typography.bodyMedium)
                }
            }
            IconButton(onClick = onSaveNote, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.BookmarkAdd, contentDescription = "Nota Ekle", modifier = Modifier.size(16.dp))
            }
        }
    }
}

private fun isNetworkAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

private fun handleGoogleTranslate(
    context: Context,
    text: String,
    openInWebView: Boolean,
    onOpenInWebView: (String) -> Unit
) {
    if (!isNetworkAvailable(context)) {
        Toast.makeText(context, "İnternet bağlantısı yok. Google Çeviri açılamıyor.", Toast.LENGTH_SHORT).show()
        return
    }

    val trimmed = if (text.length > 1500) {
        Toast.makeText(context, "Metin 1500 karaktere sınırlandı.", Toast.LENGTH_SHORT).show()
        text.take(1500)
    } else {
        text
    }

    val encoded = URLEncoder.encode(trimmed, "UTF-8")
    val url = "https://translate.google.com/?hl=tr&sl=auto&tl=tr&op=translate&text=$encoded"

    if (openInWebView) {
        onOpenInWebView(url)
    } else {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            onOpenInWebView(url)
        }
    }
}

private fun shareText(context: Context, text: String) {
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, text)
        type = "text/plain"
    }
    context.startActivity(Intent.createChooser(sendIntent, "Transkripti Paylaş"))
}

private fun generateSrt(lines: List<Pair<Long, Pair<String, String>>>): String {
    val sb = StringBuilder()
    var index = 1
    for (i in lines.indices) {
        val startMs = lines[i].first
        val endMs = if (i < lines.size - 1) lines[i + 1].first else (startMs + 3000)
        val textPair = lines[i].second
        val content = if (textPair.second.isNotBlank()) "${textPair.second}\n${textPair.first}" else textPair.first

        sb.append(index).append("\n")
        sb.append(formatSrtTime(startMs)).append(" --> ").append(formatSrtTime(endMs)).append("\n")
        sb.append(content).append("\n\n")
        index++
    }
    return sb.toString()
}

private fun formatSrtTime(ms: Long): String {
    val hours = ms / 3600000
    val remMs = ms % 3600000
    val minutes = remMs / 60000
    val remMinMs = remMs % 60000
    val seconds = remMinMs / 1000
    val millis = remMinMs % 1000
    return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
}
