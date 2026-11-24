package com.v2ray.ang.handler

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** State of the in-app APK download and install hand-off. */
sealed interface ApkInstallState {
    data object Idle : ApkInstallState
    data class Downloading(val progress: ApkDownloadProgress) : ApkInstallState
    data class ReadyToInstall(val version: String, val file: File) : ApkInstallState
    data class Failed(val message: String?) : ApkInstallState
}

/** Where the update dialog should send the user after an install attempt. */
sealed interface ApkInstallAttempt {
    data object Launched : ApkInstallAttempt
    data object NeedsInstallPermission : ApkInstallAttempt
    data class Failed(val message: String?) : ApkInstallAttempt
}

/**
 * Downloads a release APK into the app cache and hands it to the system package
 * installer through the existing `${applicationId}.cache` FileProvider.
 */
class AppUpdateInstaller(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow<ApkInstallState>(ApkInstallState.Idle)
    val state = _state.asStateFlow()

    private var downloadJob: Job? = null

    fun startDownload(version: String, url: String) {
        if (downloadJob?.isActive == true) return
        _state.value = ApkInstallState.Downloading(ApkDownloadProgress(0L, null))
        downloadJob = scope.launch {
            val target = File(updateDir(), apkFileName(version))
            val partial = File(target.parentFile, "${target.name}.part")
            try {
                partial.delete()
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("HTTP ${response.code}")
                    }
                    val body = response.body ?: throw IllegalStateException("Empty response")
                    val total = body.contentLength().takeIf { it > 0L }
                    var downloaded = 0L
                    body.byteStream().use { input ->
                        partial.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var lastEmit = 0L
                            while (true) {
                                coroutineContext.ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                val now = System.currentTimeMillis()
                                if (now - lastEmit >= PROGRESS_INTERVAL_MS) {
                                    lastEmit = now
                                    _state.value = ApkInstallState.Downloading(ApkDownloadProgress(downloaded, total))
                                }
                            }
                            output.flush()
                        }
                    }
                    _state.value = ApkInstallState.Downloading(ApkDownloadProgress(downloaded, total ?: downloaded))
                }
                if (partial.length() <= 0L) throw IllegalStateException("Empty download")
                if (!partial.renameTo(target)) {
                    partial.copyTo(target, overwrite = true)
                    partial.delete()
                }
                _state.value = ApkInstallState.ReadyToInstall(version, target)
            } catch (e: CancellationException) {
                partial.delete()
                _state.value = ApkInstallState.Idle
                throw e
            } catch (e: Exception) {
                LogUtil.w(AppConfig.TAG, "APK download failed: ${e.message}")
                partial.delete()
                _state.value = ApkInstallState.Failed(e.message)
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _state.value = ApkInstallState.Idle
    }

    fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Sends the downloaded APK to the system installer, if Android allows it. */
    fun install(state: ApkInstallState): ApkInstallAttempt {
        val ready = state as? ApkInstallState.ReadyToInstall
            ?: return ApkInstallAttempt.Failed("No downloaded package")
        if (!canInstallPackages()) return ApkInstallAttempt.NeedsInstallPermission
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.cache", ready.file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ApkInstallAttempt.Launched
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "APK installer launch failed: ${e.message}")
            ApkInstallAttempt.Failed(e.message)
        }
    }

    fun unknownSourcesSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun reset() {
        _state.value = ApkInstallState.Idle
    }

    fun dispose() {
        downloadJob?.cancel()
        scope.coroutineContext[Job]?.cancel()
    }

    private fun updateDir(): File =
        File(context.cacheDir, "updates").apply { if (!exists()) mkdirs() }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 120L
    }
}
