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
}
