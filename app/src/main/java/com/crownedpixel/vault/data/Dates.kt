package com.crownedpixel.vault.data

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object Dates {
    private val short = DateTimeFormatter.ofPattern("MMM dd", Locale.US)
    private val long = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US)
    private val day = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)

    private fun parse(raw: String?): OffsetDateTime? =
        raw?.takeIf { it.isNotBlank() }?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }

    /** "Aug 18" — the published column on the detail screen. */
    fun short(raw: String?): String = parse(raw)?.format(short) ?: "—"

    /** "Aug 18, 2026" — the release feed. */
    fun long(raw: String?): String = parse(raw)?.format(long) ?: ""

    /** "2026-08-18" — the stand-in version for a repository that publishes no version. */
    fun day(raw: String?): String = parse(raw)?.format(day) ?: ""

    fun epochMillis(raw: String?): Long? = parse(raw)?.toInstant()?.toEpochMilli()
}
