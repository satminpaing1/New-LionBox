package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.CheckUpdateResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Result of a check that did not find a new version, shown on the settings card. */
sealed interface UpdateCheckStatus {
    data object Idle : UpdateCheckStatus
    data object Checking : UpdateCheckStatus
    data object UpToDate : UpdateCheckStatus
    data class Failed(val message: String?) : UpdateCheckStatus
}

internal class MainUpdatePrompt(
    private val check: suspend () -> CheckUpdateResult,
    private val now: () -> Long,
    private val onError: (Exception) -> Unit,
    private val dismissedVersion: () -> String? = { null },
    private val onDismissVersion: (String) -> Unit = {}
) {
    private val _result = MutableStateFlow<CheckUpdateResult?>(null)
    val result = _result.asStateFlow()

    private val _status = MutableStateFlow<UpdateCheckStatus>(UpdateCheckStatus.Idle)
    val status = _status.asStateFlow()

    private var checking = false
    private var lastCheck: Long? = null
    private val dismissedVersions = mutableSetOf<String>()

    /** Silent startup check: honours the auto-check interval and dismissed versions. */
    suspend fun refresh() {
        check(isManual = false)
    }

    /** Settings card check: ignores the interval and any dismissed version. */
    suspend fun checkManually() {
        check(isManual = true)
    }

    private suspend fun check(isManual: Boolean) {
        val time = now()
        if (checking) return
        if (!isManual && lastCheck?.let { time - it < AUTO_CHECK_INTERVAL_MS } == true) return
        checking = true
        lastCheck = time
        if (isManual) _status.value = UpdateCheckStatus.Checking
        try {
            val update = check()
            val available = update.takeIf {
                it.hasUpdate && !it.latestVersion.isNullOrBlank() && !it.downloadUrl.isNullOrBlank()
            }
            if (available == null) {
                _result.value = null
                _status.value = if (isManual) UpdateCheckStatus.UpToDate else UpdateCheckStatus.Idle
            } else if (!isManual && isDismissed(available.latestVersion)) {
                _result.value = null
                _status.value = UpdateCheckStatus.Idle
            } else {
                _result.value = available
                _status.value = UpdateCheckStatus.Idle
            }
        } catch (e: CancellationException) {
            lastCheck = null
            _status.value = UpdateCheckStatus.Idle
            throw e
        } catch (e: Exception) {
            onError(e)
            _status.value = if (isManual) UpdateCheckStatus.Failed(e.message) else UpdateCheckStatus.Idle
        } finally {
            checking = false
        }
    }

    /** "Cancel"/"Later": remember the version so the automatic prompt never returns for it. */
    fun dismiss() {
        _result.value?.latestVersion?.let { version ->
            if (dismissedVersions.add(version)) onDismissVersion(version)
        }
        _result.value = null
        _status.value = UpdateCheckStatus.Idle
    }

    /** Hides the status line after the settings card has shown it. */
    fun clearStatus() {
        _status.value = UpdateCheckStatus.Idle
    }

    private fun isDismissed(version: String?): Boolean =
        version != null && (version in dismissedVersions || dismissedVersion() == version)

    private companion object {
        const val AUTO_CHECK_INTERVAL_MS = 15 * 60 * 1000L
    }
}
