package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.CheckUpdateResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MainUpdatePromptTest {
    @Test
    fun `new release prompts once and dismissal suppresses same version`() = runBlocking {
        var time = 0L
        var calls = 0
        var version = "2.3.15.7"
        val prompt = MainUpdatePrompt(
            check = { calls++; CheckUpdateResult(true, version, downloadUrl = "https://github.com/example/app.apk") },
            now = { time },
            onError = { throw it }
        )
        prompt.refresh()
        assertEquals(version, prompt.result.value?.latestVersion)
        prompt.dismiss()
        prompt.refresh()
        assertEquals(1, calls)
        time += 900_000L
        prompt.refresh()
        assertNull(prompt.result.value)
        version = "2.3.15.8"
        time += 900_000L
        prompt.refresh()
        assertEquals(version, prompt.result.value?.latestVersion)
    }

    @Test
    fun `no update or missing apk never prompts`() = runBlocking {
        for (result in listOf(CheckUpdateResult(false), CheckUpdateResult(true, "2.3.15.7"))) {
            val prompt = MainUpdatePrompt({ result }, { 0L }, { throw it })
            prompt.refresh()
            assertNull(prompt.result.value)
        }
    }

    @Test
    fun `network failure is reported without an update popup`() = runBlocking {
        var errors = 0
        val prompt = MainUpdatePrompt({ throw IllegalStateException("offline") }, { 0L }, { errors++ })
        prompt.refresh()
        assertEquals(1, errors)
        assertNull(prompt.result.value)
    }

    @Test
    fun `manual check ignores a dismissed version and always prompts`() = runBlocking {
        var stored: String? = "2.3.15.21"
        var calls = 0
        val prompt = MainUpdatePrompt(
            check = { calls++; CheckUpdateResult(true, "2.3.15.21", downloadUrl = "https://example.com/app.apk") },
            now = { 0L },
            onError = { throw it },
            dismissedVersion = { stored },
            onDismissVersion = { stored = it }
        )
        prompt.refresh()
        assertEquals(1, calls)
        assertNull(prompt.result.value)
        prompt.checkManually()
        assertEquals(2, calls)
        assertEquals("2.3.15.21", prompt.result.value?.latestVersion)
    }

    @Test
    fun `dismissing stores the version once and suppresses the next automatic prompt`() = runBlocking {
        var stored: String? = null
        var saves = 0
        var time = 0L
        val prompt = MainUpdatePrompt(
            check = { CheckUpdateResult(true, "2.3.15.21", downloadUrl = "https://example.com/app.apk") },
            now = { time },
            onError = { throw it },
            dismissedVersion = { stored },
            onDismissVersion = { saves++; stored = it }
        )
        prompt.refresh()
        prompt.dismiss()
        assertEquals("2.3.15.21", stored)
        assertEquals(1, saves)
        prompt.refresh()
        assertNull(prompt.result.value)
        assertEquals(1, saves)
    }

    @Test
    fun `manual check reports up to date and failures on the status flow`() = runBlocking {
        val upToDate = MainUpdatePrompt({ CheckUpdateResult(false, "2.3.15.21") }, { 0L }, { throw it })
        upToDate.checkManually()
        assertEquals(UpdateCheckStatus.UpToDate, upToDate.status.value)

        val failing = MainUpdatePrompt({ throw IllegalStateException("offline") }, { 0L }, {})
        failing.checkManually()
        assertEquals("offline", (failing.status.value as UpdateCheckStatus.Failed).message)
    }
}
