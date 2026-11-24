package com.v2ray.ang.ui.logcat

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.LogcatBuffer
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class LogcatViewModel(application: Application) : BaseViewModel(application) {
    private val logsetsAll: MutableList<String> = mutableListOf()
    private var currentFilter: String = ""

    private val _filteredLogs = MutableStateFlow<List<String>>(emptyList())
    val filteredLogs: StateFlow<List<String>> = _filteredLogs.asStateFlow()
    val logEntries: StateFlow<List<LogcatEntry>> = filteredLogs
        .map(::createLogcatEntries)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            LogcatBuffer.cleared.collect { cleared ->
                logsetsAll.clear()
                _filteredLogs.value = emptyList()
                if (cleared) loadLogcat()
            }
        }
    }

    fun loadLogcat() {
        launchLoading {
            try {
                val allText = withContext(Dispatchers.IO) { readLogcat() }

                logsetsAll.clear()
                logsetsAll.addAll(LogcatBuffer.cap(allText))
                applyFilter()
            } catch (e: IOException) {
                LogUtil.e(AppConfig.TAG, "Failed to get logcat", e)
            }
        }
    }

    private fun readLogcat(): List<String> {
        val lst = LinkedHashSet<String>()
        lst.add("logcat")
        lst.add("-d")
        lst.add("-v")
        lst.add("time")
        lst.add("-s")
        lst.add("GoLog,${AppConfig.ANG_PACKAGE},AndroidRuntime,System.err")
        val process = Runtime.getRuntime().exec(lst.toTypedArray())
        return process.inputStream.bufferedReader().use { it.readLines() }.reversed()
    }

    fun copyLogcat() {
        val all = filteredLogs.value.joinToString("\n")
        Utils.setClipboard(app, all)
        toast(R.string.toast_success)
    }

    fun clearLogcat() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    Runtime.getRuntime().exec(arrayOf("logcat", "-c")).waitFor()
                }
                logsetsAll.clear()
                _filteredLogs.value = emptyList()
            } catch (e: IOException) {
                LogUtil.e(AppConfig.TAG, "Failed to clear logcat", e)
            }
        }
    }

    fun filter(content: String?) {
        currentFilter = content?.trim() ?: ""
        applyFilter()
    }

    private fun applyFilter() {
        val matched = if (currentFilter.isEmpty()) {
            logsetsAll.toList()
        } else {
            logsetsAll.filter { it.contains(currentFilter) }
        }
        _filteredLogs.value = LogcatBuffer.cap(matched)
    }
}

data class LogcatEntry(val key: String, val text: String)

internal fun createLogcatEntries(logs: List<String>): List<LogcatEntry> {
    val occurrences = HashMap<String, Int>()
    // Identical lines are valid. Count oldest-first so filtering other lines or
    // prepending newer entries does not change the keys of existing occurrences.
    return logs.asReversed().map { line ->
        val occurrence = occurrences.getOrDefault(line, 0)
        occurrences[line] = occurrence + 1
        LogcatEntry("$occurrence:$line", line)
    }.asReversed()
}
