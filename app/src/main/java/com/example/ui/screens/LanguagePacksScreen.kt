package com.example.ui.screens

import android.os.StatFs
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LanguagePack
import com.example.data.model.PackStatus
import com.example.ui.AppViewModel

@Composable
fun LanguagePacksScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val packs by viewModel.languagePacks.collectAsState()
    val preferences by viewModel.preferences.collectAsState()

    var testResultDialogText by remember { mutableStateOf<String?>(null) }
    var testingPackCode by remember { mutableStateOf<String?>(null) }
    var pendingArabicWarningPack by remember { mutableStateOf<LanguagePack?>(null) }

    // Check available disk space
    val availableSpaceMB = remember {
        try {
            val stat = StatFs(context.filesDir.path)
            stat.availableBytes / (1024 * 1024)
        } catch (e: Exception) {
            -1L
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Storage & Wi-Fi settings banner
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Kullanılabilir Alan: ${if (availableSpaceMB >= 0) "$availableSpaceMB MB" else "Hesaplanıyor"}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Sadece Wi-Fi ile İndir", style = MaterialTheme.typography.bodyMedium)
                            Text("Mobil veriyi korur", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(
                        checked = preferences.wifiOnlyDownload,
                        onCheckedChange = { viewModel.setWifiOnlyDownload(it) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Çevrimdışı Dil Paketleri",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Bir kez indirin, uçak modunda ve internetsiz kullanın.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(packs, key = { it.code }) { pack ->
                val isReady = viewModel.langManager.isModelReady(pack)
                val isSelected = preferences.selectedLanguageCode == pack.code

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        else MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = pack.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (pack.isTurkish) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = Color(0xFF06B6D4).copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "Doğrudan Yazıya Dökme",
                                                color = Color(0xFF06B6D4),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = "Boyut: ~${pack.voskSizeMB} MB (Vosk) + ML Kit",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Status badge
                            when (pack.status) {
                                is PackStatus.Ready -> {
                                    Surface(
                                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "Hazır",
                                            color = Color(0xFF10B981),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                is PackStatus.Downloading -> {
                                    val progress = (pack.status as PackStatus.Downloading).progressPercent
                                    Text(
                                        text = "%$progress",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                                is PackStatus.Error -> {
                                    Surface(
                                        color = Color(0xFFEF4444).copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "Hata",
                                            color = Color(0xFFEF4444),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                is PackStatus.NotDownloaded -> {
                                    if (isReady) {
                                        Surface(
                                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "Hazır",
                                                color = Color(0xFF10B981),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "İndirilmedi",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Downloading progress indicator
                        if (pack.status is PackStatus.Downloading) {
                            val progress = (pack.status as PackStatus.Downloading).progressPercent
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                        }

                        // Error message text if any
                        if (pack.status is PackStatus.Error) {
                            val msg = (pack.status as PackStatus.Error).message
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = msg,
                                color = Color(0xFFEF4444),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Action buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (pack.status is PackStatus.Downloading) {
                                OutlinedButton(
                                    onClick = { viewModel.cancelDownload(pack.code) },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("İptal Et")
                                }
                            } else if (isReady || pack.status is PackStatus.Ready) {
                                // Model Test button
                                OutlinedButton(
                                    onClick = {
                                        testingPackCode = pack.code
                                        viewModel.testModel(pack) { success, msg ->
                                            testingPackCode = null
                                            testResultDialogText = msg
                                        }
                                    },
                                    enabled = testingPackCode == null,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    if (testingPackCode == pack.code) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Test Et")
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Select as active
                                Button(
                                    onClick = {
                                        viewModel.setSelectedLanguage(pack.code)
                                        Toast.makeText(context, "${pack.name} aktif dil seçildi", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSelected) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text(if (isSelected) "Seçili Dil" else "Kullan")
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                // Delete button
                                IconButton(
                                    onClick = {
                                        viewModel.deleteLanguagePack(pack) {
                                            Toast.makeText(context, "${pack.name} paketi silindi", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Sil", tint = Color(0xFFEF4444))
                                }
                            } else {
                                // Download button
                                Button(
                                    onClick = {
                                        if (pack.isLargeModel) {
                                            pendingArabicWarningPack = pack
                                        } else {
                                            viewModel.downloadLanguagePack(pack) { success, err ->
                                                if (success) {
                                                    Toast.makeText(context, "${pack.name} başarıyla indirildi!", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("İndir (${pack.voskSizeMB} MB)")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Large Model Warning Dialog (Arabic)
    if (pendingArabicWarningPack != null) {
        val pack = pendingArabicWarningPack!!
        AlertDialog(
            onDismissRequest = { pendingArabicWarningPack = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFF59E0B)) },
            title = { Text("Büyük Model Boyutu Uyarısı") },
            text = {
                Text(
                    "${pack.name} modeli (${pack.voskSizeMB} MB) geniş akustik model içerdiğinden indirmesi ve açılması zaman alabilir. Wi-Fi bağlantısı önerilir. Devam etmek istiyor musunuz?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = pendingArabicWarningPack!!
                        pendingArabicWarningPack = null
                        viewModel.downloadLanguagePack(p) { success, err ->
                            if (success) {
                                Toast.makeText(context, "${p.name} başarıyla indirildi!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("Yine de İndir")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingArabicWarningPack = null }) {
                    Text("Vazgeç")
                }
            }
        )
    }

    // Offline Test Result Dialog
    if (testResultDialogText != null) {
        AlertDialog(
            onDismissRequest = { testResultDialogText = null },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981)) },
            title = { Text("Çevrimdışı Test Sonucu") },
            text = { Text(testResultDialogText!!) },
            confirmButton = {
                Button(onClick = { testResultDialogText = null }) {
                    Text("Tamam")
                }
            }
        )
    }
}
