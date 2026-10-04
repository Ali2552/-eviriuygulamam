package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Translate : Screen("translate", "Çeviri", Icons.Default.Subtitles)
    object Transcript : Screen("transcript", "Transkript", Icons.Default.Description)
    object Search : Screen("search", "Arama", Icons.Default.Search)
    object Notes : Screen("notes", "Notlar", Icons.Default.EditNote)
    object LanguagePacks : Screen("language_packs", "Dil Paketleri", Icons.Default.Download)
    object Settings : Screen("settings", "Ayarlar", Icons.Default.Settings)
    object KnownIssues : Screen("known_issues", "Bilinen Sorunlar", Icons.Default.Info)
}

val BOTTOM_NAV_ITEMS = listOf(
    Screen.Translate,
    Screen.Transcript,
    Screen.Search,
    Screen.Notes
)
