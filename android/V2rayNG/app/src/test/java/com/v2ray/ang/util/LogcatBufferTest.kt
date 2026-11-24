package com.v2ray.ang.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LogcatBufferTest {

    @Test
    fun capKeepsTheNewestLinesWhenTheBufferOverflows() {
        val lines = (1..LogcatBuffer.MAX_LINES + 120).map { "line $it" }

        val capped = LogcatBuffer.cap(lines)

        assertEquals(LogcatBuffer.MAX_LINES, capped.size)
        assertEquals("line 1", capped.first())
        assertEquals("line ${LogcatBuffer.MAX_LINES}", capped.last())
    }

    @Test
    fun capLeavesShorterBuffersUntouched() {
        val lines = listOf("first", "second")

        assertEquals(lines, LogcatBuffer.cap(lines))
        assertTrue(LogcatBuffer.cap(emptyList()).isEmpty())
    }
}
