package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnownIssuesScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bilinen Sorunlar ve Kısıtlamalar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            IssueCard(
                icon = Icons.Default.Block,
                iconColor = Color(0xFFEF4444),
                title = "1. Bazı Uygulamaların Ses Yakalamayı Engellemesi (DRM / Gizlilik)",
                description = "Android 10+ AudioPlaybackCapture standardında uygulamalar seslerinin kaydedilmesini bayraklarla (AudioAttributes.ALLOW_CAPTURE_BY_NONE) engelleyebilir. Örneğin; bazı bankacılık uygulamaları veya telif korumalı DRM video servisleri (Netflix vb.) dahili ses akışını sessiz (peak 0) olarak gönderir. YouTube, Instagram, TikTok ve tarayıcı videoları ise çoğunlukla serbestçe yakalanabilir."
            )

            IssueCard(
                icon = Icons.Default.GraphicEq,
                iconColor = Color(0xFFF59E0B),
                title = "2. Küçük Vosk Modelleri ve Müzik / Gürültü Hassasiyeti",
                description = "Uygulama telefon hafızasını ve pilini yormamak için hızlı ve hafif (35-80 MB) Vosk modelleri kullanır. Videoda arka planda çok yüksek sesli müzik, yankı, patlama sesleri veya gürültü olduğunda kelime tanıma doğruluğu düşebilir. En yüksek başarı net konuşma içeren videolarda elde edilir."
            )

            IssueCard(
                icon = Icons.Default.Language,
                iconColor = Color(0xFF3B82F6),
                title = "3. Konuşma Dilinin Elle Seçilmesi Zorunluluğu",
                description = "Vosk çevrimdışı motoru dili otomatik olarak tahmin edemez (otomatik dil algılama için çok büyük ve internete bağımlı modeller gerekir). Bu yüzden izlediğiniz videonun dilini (İngilizce, Rusça vb.) ana ekrandan veya yüzen penceredeki dil düğmesinden seçmeniz gerekir."
            )

            IssueCard(
                icon = Icons.Default.CloudOff,
                iconColor = Color(0xFF10B981),
                title = "4. Çevrimdışı Çalışma ve İlk İndirme Gereksinimi",
                description = "Uygulama hiçbir bulut sunucusuna ses verisi göndermez; tamamen cihazınızda çevrimdışı çalışır. Ancak ilk kullanımda seçtiğiniz dilin Vosk modelini ve ML Kit çeviri paketini internet üzerinden 'Dil Paketleri' sekmesinden bir defaya mahsus indirmeniz gerekmektedir."
            )
        }
    }
}

@Composable
private fun IssueCard(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    description: String
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
        }
    }
}
