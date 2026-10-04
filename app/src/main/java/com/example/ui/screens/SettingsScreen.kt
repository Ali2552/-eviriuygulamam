package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppViewModel
import com.example.ui.components.AudioPeakMeter

@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    onNavigateToKnownIssues: () -> Unit
) {
    val preferences by viewModel.preferences.collectAsState()
    val isRunning by viewModel.isServiceRunning.collectAsState()
    val currentPeak by viewModel.currentPeak.collectAsState()
    val statusText by viewModel.serviceStatus.collectAsState()

    var fontSize by remember(preferences.overlaySettings.fontSizeSp) {
        mutableFloatStateOf(preferences.overlaySettings.fontSizeSp)
    }

    var bgAlphaPercent by remember(preferences.overlaySettings.backgroundColor) {
        val alpha = (preferences.overlaySettings.backgroundColor ushr 24) and 0xFF
        mutableFloatStateOf((alpha / 255f) * 100f)
    }

    val availableColors = listOf(
        Pair("Beyaz", 0xFFFFFFFF),
        Pair("Sarı", 0xFFFACC15),
        Pair("Yeşil", 0xFF4ADE80),
        Pair("Açık Mavi", 0xFF38BDF8)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Live Audio Peak Meter test
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Canlı Ses Seviyesi Göstergesi",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                AudioPeakMeter(
                    peak = currentPeak,
                    statusText = statusText,
                    isRunning = isRunning
                )
            }
        }

        // Overlay Style Settings Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Yüzen Alt Yazı (Overlay) Görünümü",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Font Size Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Yazı Boyutu", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("${fontSize.toInt()} sp", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }

                Slider(
                    value = fontSize,
                    onValueChange = {
                        fontSize = it
                        viewModel.updateOverlaySettings(preferences.overlaySettings.copy(fontSizeSp = it))
                    },
                    valueRange = 12f..26f,
                    steps = 14
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Text Color Picker
                Text("Yazı Rengi", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    availableColors.forEach { (name, colorLong) ->
                        val isSelected = preferences.overlaySettings.textColor == colorLong
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(colorLong))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                    shape = CircleShape
                                )
                                .clickable {
                                    viewModel.updateOverlaySettings(preferences.overlaySettings.copy(textColor = colorLong))
                                }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Background Opacity Slider (capped at 80% per prompt requirements)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Arka Plan Şeffaflığı (Maks %80)", style = MaterialTheme.typography.bodyMedium)
                    Text("%${bgAlphaPercent.toInt()}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }

                Slider(
                    value = bgAlphaPercent,
                    onValueChange = {
                        bgAlphaPercent = it.coerceIn(10f, 80f)
                        val alphaInt = ((bgAlphaPercent / 100f) * 255).toInt().coerceIn(25, 204)
                        val newBgColor = (preferences.overlaySettings.backgroundColor and 0x00FFFFFF) or (alphaInt.toLong() shl 24)
                        viewModel.updateOverlaySettings(preferences.overlaySettings.copy(backgroundColor = newBgColor))
                    },
                    valueRange = 10f..80f
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Mini preview box of overlay
                Text("Önizleme", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))

                val previewBgColor = Color(preferences.overlaySettings.backgroundColor.toInt())
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(previewBgColor)
                        .border(1.dp, Color(0xFF475569), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "Türkçe alt yazı örnek metni",
                            color = Color(preferences.overlaySettings.textColor),
                            fontSize = fontSize.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "English original preview subtitle",
                            color = Color(0xFF94A3B8),
                            fontSize = (fontSize * 0.82f).sp
                        )
                    }
                }
            }
        }

        // Google Translate & External Links Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Google Çeviri Tercihi",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Uygulama İçi WebView ile Aç", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Açıkken harici tarayıcıya gitmeden uygulama içinde Google Çeviri açılır.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = preferences.openTranslateInWebView,
                        onCheckedChange = { viewModel.setOpenTranslateInWebView(it) }
                    )
                }
            }
        }

        // Known Issues & Limitations Button
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            onClick = onNavigateToKnownIssues
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Bilinen Sorunlar ve Kısıtlamalar",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "DRM engelleri, arka plan müzik gürültüsü, model limitleri hakkında bilgi",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
