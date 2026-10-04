package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.VideoSubtitleApplication
import com.example.data.db.NoteEntity
import com.example.data.db.SearchHistoryEntity
import com.example.data.db.TranscriptEntity
import com.example.data.db.TranscriptLineEntity
import com.example.data.model.AppPreferences
import com.example.data.model.LanguagePack
import com.example.data.model.LiveSubtitle
import com.example.data.model.OverlaySettings
import com.example.data.model.SubtitleMode
import com.example.service.ServiceController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as VideoSubtitleApplication
    private val transcriptRepo = app.transcriptRepository
    private val noteRepo = app.noteRepository
    private val searchRepo = app.searchHistoryRepository
    private val settingsRepo = app.settingsRepository
    val langManager = app.languagePackManager

    val isServiceRunning: StateFlow<Boolean> = ServiceController.isRunning
    val serviceStatus: StateFlow<String> = ServiceController.statusText
    val currentPeak: StateFlow<Float> = ServiceController.currentPeak
    val liveSubtitles: StateFlow<List<LiveSubtitle>> = ServiceController.liveSubtitles

    val languagePacks: StateFlow<List<LanguagePack>> = langManager.packsState

    val preferences: StateFlow<AppPreferences> = settingsRepo.preferences

    val transcripts: StateFlow<List<TranscriptEntity>> = transcriptRepo.allTranscripts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<NoteEntity>> = noteRepo.allNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentSearches: StateFlow<List<SearchHistoryEntity>> = searchRepo.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedTranscriptLines = MutableStateFlow<List<TranscriptLineEntity>>(emptyList())
    val selectedTranscriptLines: StateFlow<List<TranscriptLineEntity>> = _selectedTranscriptLines.asStateFlow()

    private val _recentlyDeletedNote = MutableStateFlow<NoteEntity?>(null)
    val recentlyDeletedNote: StateFlow<NoteEntity?> = _recentlyDeletedNote.asStateFlow()

    fun setSelectedLanguage(code: String) {
        settingsRepo.setSelectedLanguage(code)
    }

    fun setSubtitleMode(mode: SubtitleMode) {
        settingsRepo.setSubtitleMode(mode)
    }

    fun setOpenTranslateInWebView(enabled: Boolean) {
        settingsRepo.setOpenTranslateInWebView(enabled)
    }

    fun setWifiOnlyDownload(enabled: Boolean) {
        settingsRepo.setWifiOnlyDownload(enabled)
    }

    fun updateOverlaySettings(settings: OverlaySettings) {
        settingsRepo.updateOverlaySettings(settings)
    }

    fun loadTranscriptLines(transcriptId: Long) {
        viewModelScope.launch {
            transcriptRepo.getLinesForTranscript(transcriptId).collect { lines ->
                _selectedTranscriptLines.value = lines
            }
        }
    }

    fun deleteTranscript(id: Long) {
        viewModelScope.launch {
            transcriptRepo.deleteTranscriptById(id)
        }
    }

    fun addNote(title: String, content: String, onComplete: ((Long) -> Unit)? = null) {
        viewModelScope.launch {
            val id = noteRepo.insertNote(title, content)
            onComplete?.invoke(id)
        }
    }

    fun updateNote(note: NoteEntity) {
        viewModelScope.launch {
            noteRepo.updateNote(note)
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            _recentlyDeletedNote.value = note
            noteRepo.deleteNoteById(note.id)
        }
    }

    fun undoDeleteNote() {
        val note = _recentlyDeletedNote.value ?: return
        viewModelScope.launch {
            noteRepo.restoreNote(note)
            _recentlyDeletedNote.value = null
        }
    }

    fun addSearchHistory(query: String, filterType: String) {
        viewModelScope.launch {
            searchRepo.addSearch(query, filterType)
        }
    }

    fun deleteSearchHistory(id: Long) {
        viewModelScope.launch {
            searchRepo.deleteSearch(id)
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            searchRepo.clearHistory()
        }
    }

    fun downloadLanguagePack(pack: LanguagePack, onComplete: (Boolean, String?) -> Unit) {
        val wifiOnly = preferences.value.wifiOnlyDownload
        langManager.downloadPack(pack, wifiOnly, onComplete)
    }

    fun cancelDownload(packCode: String) {
        langManager.cancelDownload(packCode)
    }

    fun deleteLanguagePack(pack: LanguagePack, onComplete: (Boolean) -> Unit) {
        langManager.deletePack(pack, onComplete)
    }

    fun testModel(pack: LanguagePack, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = langManager.testOfflineModel(pack)
            onResult(result.first, result.second)
        }
    }
}
