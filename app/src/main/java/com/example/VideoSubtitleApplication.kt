package com.example

import android.app.Application
import com.example.data.db.AppDatabase
import com.example.data.repository.LanguagePackManager
import com.example.data.repository.NoteRepository
import com.example.data.repository.SearchHistoryRepository
import com.example.data.repository.SettingsRepository
import com.example.data.repository.TranscriptRepository

class VideoSubtitleApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var transcriptRepository: TranscriptRepository
        private set

    lateinit var noteRepository: NoteRepository
        private set

    lateinit var searchHistoryRepository: SearchHistoryRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var languagePackManager: LanguagePackManager
        private set

    override fun onCreate() {
        super.onCreate()

        database = AppDatabase.getDatabase(this)
        transcriptRepository = TranscriptRepository(database.transcriptDao())
        noteRepository = NoteRepository(database.noteDao())
        searchHistoryRepository = SearchHistoryRepository(database.searchHistoryDao())
        settingsRepository = SettingsRepository(this)
        languagePackManager = LanguagePackManager(this)
    }
}
