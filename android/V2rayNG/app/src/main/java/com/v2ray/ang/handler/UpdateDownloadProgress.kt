package com.v2ray.ang.handler

/** Live byte progress of an in-app APK download, safe to format without Android APIs. */
data class ApkDownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long?
) {
    /** Percent complete, or null while the server has not reported a content length. */
    val percent: Int?
        get() {
            val total = totalBytes ?: return null
            if (total <= 0L) return null
            return ((downloadedBytes * 100) / total).toInt().coerceIn(0, 100)
        }

    /** "42% · 12.4 MB / 29.1 MB", or "12.4 MB downloaded" when the size is unknown. */
    fun label(): String {
        val downloaded = formatBytes(downloadedBytes)
        val total = totalBytes
        val pct = percent
        return if (total != null && pct != null) {
            "$pct% · $downloaded / ${formatBytes(total)}"
        } else {
            "$downloaded downloaded"
        }
    }
}

/** Human-readable megabyte string with one decimal, for download progress text. */
fun formatBytes(bytes: Long): String {
    val clamped = bytes.coerceAtLeast(0L)
    val mb = clamped / (1024.0 * 1024.0)
    return if (mb >= 100) "${mb.toInt()} MB" else String.format(java.util.Locale.US, "%.1f MB", mb)
}

/** File-safe APK name for a release version such as "2.3.15.21". */
fun apkFileName(version: String): String {
    val safe = version.filter { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }
        .takeIf { name -> name.any { it.isLetterOrDigit() } }
        ?: "latest"
    return "lionbox-$safe.apk"
}
