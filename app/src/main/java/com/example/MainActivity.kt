package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.model.SubtitleMode
import com.example.service.ServiceController
import com.example.service.SubtitleForegroundService
import com.example.ui.AppViewModel
import com.example.ui.components.PermissionWizardDialog
import com.example.ui.navigation.BOTTOM_NAV_ITEMS
import com.example.ui.navigation.Screen
import com.example.ui.screens.GoogleTranslateWebViewScreen
import com.example.ui.screens.KnownIssuesScreen
import com.example.ui.screens.LanguagePacksScreen
import com.example.ui.screens.NotesScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TranscriptScreen
import com.example.ui.screens.TranslateScreen
import com.example.ui.theme.VideoSubtitleTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    private var pendingLanguageCode: String = "en"
    private var pendingMode: SubtitleMode = SubtitleMode.TRANSCRIPT_AND_TRANSLATE
    private var showPermissionWizard by mutableStateOf(false)

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            launchSubtitleService(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "Ekran/Ses yakalama izni verilmedi.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            VideoSubtitleTheme {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    UnsupportedAndroidVersionScreen()
                } else {
                    MainAppContent()
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MainAppContent() {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        var inAppTranslateUrl by remember { mutableStateOf<String?>(null) }

        if (inAppTranslateUrl != null) {
            GoogleTranslateWebViewScreen(
                url = inAppTranslateUrl!!,
                onBack = { inAppTranslateUrl = null }
            )
            return
        }

        PermissionWizardDialog(
            isOpen = showPermissionWizard,
            onDismiss = { showPermissionWizard = false },
            onAllGranted = {
                showPermissionWizard = false
                requestMediaProjection()
            }
        )

        Scaffold(
            topBar = {
                if (currentRoute != Screen.KnownIssues.route) {
                    TopAppBar(
                        title = {
                            Text(
                                text = getScreenTitle(currentRoute),
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        actions = {
                            IconButton(onClick = { navController.navigate(Screen.LanguagePacks.route) }) {
                                Icon(Icons.Default.Download, contentDescription = "Dil Paketleri")
                            }
                            IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                                Icon(Icons.Default.Settings, contentDescription = "Ayarlar")
                            }
                        }
                    )
                }
            },
            bottomBar = {
                val showBottomBar = BOTTOM_NAV_ITEMS.any { it.route == currentRoute }
                if (showBottomBar) {
                    NavigationBar {
                        BOTTOM_NAV_ITEMS.forEach { screen ->
                            NavigationBarItem(
                                icon = { Icon(screen.icon, contentDescription = screen.title) },
                                label = { Text(screen.title) },
                                selected = currentRoute == screen.route,
                                onClick = {
                                    if (currentRoute != screen.route) {
                                        navController.navigate(screen.route) {
                                            popUpTo(Screen.Translate.route) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = Screen.Translate.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                composable(Screen.Translate.route) {
                    TranslateScreen(
                        viewModel = viewModel,
                        onStartRequested = { langCode, mode ->
                            pendingLanguageCode = langCode
                            pendingMode = mode
                            checkPermissionsAndStart()
                        },
                        onStopRequested = {
                            stopSubtitleService()
                        },
                        onToggleOverlayRequested = {
                            toggleOverlay()
                        },
                        onNavigateToLanguagePacks = {
                            navController.navigate(Screen.LanguagePacks.route)
                        }
                    )
                }

                composable(Screen.Transcript.route) {
                    TranscriptScreen(
                        viewModel = viewModel,
                        onOpenGoogleTranslateWebView = { url ->
                            inAppTranslateUrl = url
                        }
                    )
                }

                composable(Screen.Search.route) {
                    SearchScreen(viewModel = viewModel)
                }

                composable(Screen.Notes.route) {
                    NotesScreen(viewModel = viewModel)
                }

                composable(Screen.LanguagePacks.route) {
                    LanguagePacksScreen(viewModel = viewModel)
                }

                composable(Screen.Settings.route) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onNavigateToKnownIssues = {
                            navController.navigate(Screen.KnownIssues.route)
                        }
                    )
                }

                composable(Screen.KnownIssues.route) {
                    KnownIssuesScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }

    private fun checkPermissionsAndStart() {
        val hasAudio = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val hasOverlay = Settings.canDrawOverlays(this)

        val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        if (!hasAudio || !hasOverlay || !hasNotification) {
            showPermissionWizard = true
        } else {
            requestMediaProjection()
        }
    }

    private fun requestMediaProjection() {
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        // Per requirement: Always launch fresh permission flow, do not reuse old projection
        val captureIntent = mpManager.createScreenCaptureIntent()
        mediaProjectionLauncher.launch(captureIntent)
    }

    private fun launchSubtitleService(resultCode: Int, resultData: Intent) {
        val intent = Intent(this, SubtitleForegroundService::class.java).apply {
            action = ServiceController.ACTION_START
            putExtra(ServiceController.EXTRA_RESULT_CODE, resultCode)
            putExtra(ServiceController.EXTRA_RESULT_DATA, resultData)
            putExtra(ServiceController.EXTRA_LANGUAGE_CODE, pendingLanguageCode)
            putExtra(ServiceController.EXTRA_MODE, pendingMode.name)
        }

        try {
            ContextCompat.startForegroundService(this, intent)
        } catch (e: Throwable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e is android.app.ForegroundServiceStartNotAllowedException
            ) {
                Toast.makeText(
                    this,
                    "Servis yalnızca uygulama ön plandayken başlatılabilir.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    this,
                    "Servis başlatılamadı: ${e.localizedMessage}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun stopSubtitleService() {
        val intent = Intent(this, SubtitleForegroundService::class.java).apply {
            action = ServiceController.ACTION_STOP
        }
        startService(intent)
    }

    private fun toggleOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Lütfen diğer uygulamaların üzerinde gösterme iznini açın.", Toast.LENGTH_SHORT).show()
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            return
        }
        val intent = Intent(this, SubtitleForegroundService::class.java).apply {
            action = ServiceController.ACTION_TOGGLE_OVERLAY
        }
        startService(intent)
    }

    private fun getScreenTitle(route: String?): String = when (route) {
        Screen.Translate.route -> "Video Alt Yazı"
        Screen.Transcript.route -> "Transkript"
        Screen.Search.route -> "Arama"
        Screen.Notes.route -> "Notlar"
        Screen.LanguagePacks.route -> "Dil Paketleri"
        Screen.Settings.route -> "Ayarlar"
        Screen.KnownIssues.route -> "Bilinen Sorunlar"
        else -> "Video Alt Yazı"
    }

    @Composable
    private fun UnsupportedAndroidVersionScreen() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Bu uygulama dahili ses yakalama (AudioPlaybackCapture) için Android 10 (API 29) ve üzerini gerektirir.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
