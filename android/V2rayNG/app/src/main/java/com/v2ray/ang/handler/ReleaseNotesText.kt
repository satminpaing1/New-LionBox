package com.v2ray.ang.handler

/**
 * Turns GitHub release markdown into plain lines for the update dialog:
 * drops heading/list marks and emphasis, keeps the readable text.
 */
fun plainReleaseNotes(text: String?): String {
    if (text.isNullOrBlank()) return ""
    return text
        .replace("\r\n", "\n")
        .replace(Regex("!\\[[^\\]]*]\\([^)]*\\)"), "")
        .replace(Regex("\\[([^\\]]+)]\\(([^)]*)\\)"), "$1 ($2)")
        .replace("**", "")
        .replace("__", "")
        .replace("`", "")
        .lineSequence()
        .map { it.trim().removePrefix("#").trim() }
        .filter { it.isNotBlank() && !it.startsWith("<!--") }
        .joinToString("\n")
}
