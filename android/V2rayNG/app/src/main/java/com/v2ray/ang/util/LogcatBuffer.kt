package com.v2ray.ang.util

import com.v2ray.ang.AppConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Keeps the on-device logcat buffer bounded and clears it on every VPN toggle so a long
 * session cannot accumulate hours of lines in memory.
 */
object LogcatBuffer {
    /** Ring-buffer size: only the newest lines are kept while a session runs. */
    const val MAX_LINES = 500

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _cleared = MutableSharedFlow<Boolean>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Emits whether the system buffer was cleared, so open Logs screens refresh themselves. */
    val cleared: SharedFlow<Boolean> = _cleared.asSharedFlow()

    /** Clears the system logcat ring and notifies observers. Safe to call from any thread. */
    fun clearForNewSession() {
        scope.launch {
            val cleared = try {
                Runtime.getRuntime().exec(arrayOf("logcat", "-c")).waitFor() == 0
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to clear logcat buffer", e)
                false
            }
            _cleared.tryEmit(cleared)
        }
    }

    /** Newest-first list capped to [MAX_LINES]. */
    fun cap(lines: List<String>): List<String> =
        if (lines.size > MAX_LINES) lines.subList(0, MAX_LINES) else lines
}
