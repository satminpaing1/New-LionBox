package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.CheckUpdateResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class MainUpdatePrompt(
    private val check: suspend () -> CheckUpdateResult,
    private val now: () -> Long,
    private val onError: (Exception) -> Unit
) {
    private val _result = MutableStateFlow<CheckUpdateResult?>(null)
    val result = _result.asStateFlow()
    private var checking = false
    private var lastCheck: Long? = null
    private val dismissedVersions = mutableSetOf<String>()

    suspend fun refresh() {
        val time = now()
        if (checking || lastCheck?.let { time - it < 15 * 60 * 1000L } == true) return
        checking = true
        lastCheck = time
        try {
            val update = check()
            _result.value = update.takeIf {
                it.hasUpdate && !it.latestVersion.isNullOrBlank() &&
                    !it.downloadUrl.isNullOrBlank() && it.latestVersion !in dismissedVersions
            }
        } catch (e: CancellationException) {
            lastCheck = null
            throw e
        } catch (e: Exception) {
            onError(e)
        } finally {
            checking = false
        }
    }

    fun dismiss() {
        _result.value?.latestVersion?.let { dismissedVersions.add(it) }
        _result.value = null
    }
}
