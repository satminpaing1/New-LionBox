package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class UpdateDownloadProgressTest {
    @Test
    fun `percent and label follow downloaded bytes and reported size`() {
        val progress = ApkDownloadProgress(4L * 1024 * 1024, 8L * 1024 * 1024)
        assertEquals(50, progress.percent)
        assertEquals("50% · 4.0 MB / 8.0 MB", progress.label())
    }

    @Test
    fun `unknown size reports downloaded megabytes only`() {
        val progress = ApkDownloadProgress(1536L * 1024, null)
        assertNull(progress.percent)
        assertEquals("1.5 MB downloaded", progress.label())
    }

    @Test
    fun `percent is clamped and apk names are file safe`() {
        assertEquals(100, ApkDownloadProgress(12L, 10L).percent)
        assertEquals("lionbox-2.3.15.21.apk", apkFileName("2.3.15.21"))
        assertEquals("lionbox-latest.apk", apkFileName("../"))
    }
}
