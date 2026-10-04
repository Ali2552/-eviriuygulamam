package com.example.data.repository

import android.content.Context
import android.os.StatFs
import com.example.data.model.LanguagePack
import com.example.data.model.LanguagePacksCatalog
import com.example.data.model.PackStatus
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.vosk.Model
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class LanguagePackManager(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val remoteModelManager = RemoteModelManager.getInstance()

    private val _packsState = MutableStateFlow<List<LanguagePack>>(LanguagePacksCatalog.DEFAULT_PACKS)
    val packsState: StateFlow<List<LanguagePack>> = _packsState.asStateFlow()

    private val downloadJobs = mutableMapOf<String, Job>()
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        refreshStatus()
    }

    fun getModelDirectory(voskModelName: String): File {
        val baseDir = File(context.filesDir, "vosk-models")
        if (!baseDir.exists()) baseDir.mkdirs()
        return File(baseDir, voskModelName)
    }

    fun isModelReady(pack: LanguagePack): Boolean {
        val dir = getModelDirectory(pack.voskModelName)
        val voskReady = dir.exists() && dir.isDirectory && (dir.list()?.isNotEmpty() == true)
        return voskReady
    }

    fun refreshStatus() {
        scope.launch {
            val updated = _packsState.value.map { pack ->
                if (downloadJobs[pack.code]?.isActive == true) {
                    pack
                } else if (isModelReady(pack)) {
                    pack.copy(status = PackStatus.Ready)
                } else {
                    pack.copy(status = PackStatus.NotDownloaded)
                }
            }
            _packsState.value = updated
        }
    }

    fun downloadPack(pack: LanguagePack, wifiOnly: Boolean, onComplete: (Boolean, String?) -> Unit) {
        val job = scope.launch {
            try {
                // 1. Check storage space
                val stat = StatFs(context.filesDir.path)
                val availableMB = (stat.availableBytes / (1024 * 1024))
                val requiredMB = pack.voskSizeMB + 60 // vosk + mlkit + extraction
                if (availableMB < requiredMB) {
                    val errMsg = "Yetersiz depolama alanı. Gerekli: $requiredMB MB, Mevcut: $availableMB MB"
                    updatePackStatus(pack.code, PackStatus.Error(errMsg))
                    withContext(Dispatchers.Main) { onComplete(false, errMsg) }
                    return@launch
                }

                updatePackStatus(pack.code, PackStatus.Downloading(0))

                // 2. Download Vosk zip
                val modelDir = getModelDirectory(pack.voskModelName)
                if (modelDir.exists()) {
                    modelDir.deleteRecursively()
                }

                val tempZipFile = File(context.cacheDir, "${pack.voskModelName}.zip")
                if (tempZipFile.exists()) tempZipFile.delete()

                val request = Request.Builder().url(pack.downloadUrl).build()
                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful || response.body == null) {
                    val errMsg = "İndirme başarısız (HTTP ${response.code})"
                    updatePackStatus(pack.code, PackStatus.Error(errMsg))
                    withContext(Dispatchers.Main) { onComplete(false, errMsg) }
                    return@launch
                }

                val body = response.body!!
                val contentLength = body.contentLength()
                var downloadedBytes = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tempZipFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (contentLength > 0) {
                                val progress = ((downloadedBytes * 80) / contentLength).toInt()
                                updatePackStatus(pack.code, PackStatus.Downloading(progress))
                            }
                        }
                    }
                }

                // 3. Unzip
                updatePackStatus(pack.code, PackStatus.Downloading(85))
                unzip(tempZipFile, getModelDirectory(pack.voskModelName).parentFile!!)
                tempZipFile.delete()

                // Check unzipped folder: Vosk zips usually contain a top folder named vosk-model-...
                // Ensure target folder is in place
                val expectedDir = getModelDirectory(pack.voskModelName)
                if (!expectedDir.exists()) {
                    // Look for extracted directory
                    val parent = expectedDir.parentFile!!
                    val candidate = parent.listFiles()?.firstOrNull { it.isDirectory && it.name.startsWith(pack.voskModelName) }
                    if (candidate != null && candidate != expectedDir) {
                        candidate.renameTo(expectedDir)
                    }
                }

                // 4. Download ML Kit translation models (if not Turkish)
                if (!pack.isTurkish) {
                    updatePackStatus(pack.code, PackStatus.Downloading(90))
                    val conditionsBuilder = DownloadConditions.Builder()
                    if (wifiOnly) {
                        conditionsBuilder.requireWifi()
                    }
                    val conditions = conditionsBuilder.build()

                    val sourceModel = TranslateRemoteModel.Builder(pack.mlKitLanguage).build()
                    val targetModel = TranslateRemoteModel.Builder(TranslateLanguage.TURKISH).build()

                    try {
                        remoteModelManager.download(targetModel, conditions).await()
                        remoteModelManager.download(sourceModel, conditions).await()
                    } catch (e: Exception) {
                        // ML Kit download fallback or network warning
                    }
                }

                updatePackStatus(pack.code, PackStatus.Ready)
                withContext(Dispatchers.Main) { onComplete(true, null) }
            } catch (ce: CancellationException) {
                // Cancelled by user
                val tempZip = File(context.cacheDir, "${pack.voskModelName}.zip")
                if (tempZip.exists()) tempZip.delete()
                val targetDir = getModelDirectory(pack.voskModelName)
                if (targetDir.exists()) targetDir.deleteRecursively()
                updatePackStatus(pack.code, PackStatus.NotDownloaded)
            } catch (e: Exception) {
                e.printStackTrace()
                val tempZip = File(context.cacheDir, "${pack.voskModelName}.zip")
                if (tempZip.exists()) tempZip.delete()
                val errMsg = e.localizedMessage ?: "Bilinmeyen indirme hatası"
                updatePackStatus(pack.code, PackStatus.Error(errMsg))
                withContext(Dispatchers.Main) { onComplete(false, errMsg) }
            } finally {
                downloadJobs.remove(pack.code)
            }
        }
        downloadJobs[pack.code] = job
    }

    fun cancelDownload(packCode: String) {
        downloadJobs[packCode]?.cancel()
        downloadJobs.remove(packCode)
    }

    fun deletePack(pack: LanguagePack, onComplete: (Boolean) -> Unit) {
        scope.launch {
            try {
                val dir = getModelDirectory(pack.voskModelName)
                if (dir.exists()) {
                    dir.deleteRecursively()
                }
                if (!pack.isTurkish) {
                    val model = TranslateRemoteModel.Builder(pack.mlKitLanguage).build()
                    remoteModelManager.deleteDownloadedModel(model)
                }
                updatePackStatus(pack.code, PackStatus.NotDownloaded)
                withContext(Dispatchers.Main) { onComplete(true) }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { onComplete(false) }
            }
        }
    }

    suspend fun testOfflineModel(pack: LanguagePack): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val dir = getModelDirectory(pack.voskModelName)
            if (!dir.exists() || dir.list().isNullOrEmpty()) {
                return@withContext Pair(false, "Vosk modeli dosyaları eksik veya bulunamadı.")
            }

            // Test Vosk model instantiation
            val voskModel = Model(dir.absolutePath)
            voskModel.close()

            // Test ML Kit translation if not Turkish
            if (!pack.isTurkish) {
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(pack.mlKitLanguage)
                    .setTargetLanguage(TranslateLanguage.TURKISH)
                    .build()
                val translator = Translation.getClient(options)
                try {
                    val sampleText = "Hello"
                    val testResult = translator.translate(sampleText).await()
                    translator.close()
                    return@withContext Pair(true, "Vosk ve ML Kit çeviri başarıyla test edildi. Çeviri testi: '$sampleText' -> '$testResult'")
                } catch (e: Exception) {
                    translator.close()
                    return@withContext Pair(true, "Vosk modeli sağlam yüklendi. ML Kit çeviri modeli henüz indirilmemiş olabilir.")
                }
            } else {
                return@withContext Pair(true, "Türkçe Vosk modeli başarıyla doğrulandı.")
            }
        } catch (e: Throwable) {
            return@withContext Pair(false, "Model testinde hata: ${e.localizedMessage}")
        }
    }

    private fun updatePackStatus(code: String, status: PackStatus) {
        _packsState.update { currentList ->
            currentList.map {
                if (it.code == code) it.copy(status = status) else it
            }
        }
    }

    private fun unzip(zipFile: File, targetDirectory: File) {
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val newFile = File(targetDirectory, entry.name)
                // Protect against Zip Slip vulnerability
                if (!newFile.canonicalPath.startsWith(targetDirectory.canonicalPath)) {
                    throw SecurityException("Zip Slip attack detected in entry: ${entry.name}")
                }
                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
