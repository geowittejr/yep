package com.georgewitte.yep.logic

import java.util.UUID

/** One thing the user wants to be sure about, e.g. "Front door locked". */
data class Check(
    val id: String,
    val name: String,
    val emoji: String,
    /** Confirmation times in epoch millis, newest first. */
    val history: List<Long>,
) {
    val lastConfirmed: Long? get() = history.firstOrNull()
}

enum class Freshness { NEVER, FRESH, STALE }

/** Pure list operations. The UI keeps an immutable list and swaps it for the result of these. */
object Checks {
    const val MAX_NAME_LENGTH = 40
    const val MAX_HISTORY = 50

    /** A confirmation counts as "fresh" for this long. */
    const val FRESH_WINDOW_MS = 12L * 60 * 60 * 1000

    /** Emoji from Unicode 6–9 only, so they render on every supported Android version. */
    val EMOJI_CHOICES = listOf(
        "🔒", "🔑", "🚪", "🏠", "🔥", "🍳", "💡", "🔌",
        "💊", "🚗", "🐕", "🐈", "🌱", "💧", "👶", "✅",
    )

    fun newId(): String = UUID.randomUUID().toString()

    fun defaults(idGen: () -> String = ::newId): List<Check> = listOf(
        Check(idGen(), "Front door locked", "🔒", emptyList()),
        Check(idGen(), "Stove off", "🔥", emptyList()),
        Check(idGen(), "Took my meds", "💊", emptyList()),
        Check(idGen(), "Car locked", "🚗", emptyList()),
    )

    fun freshness(now: Long, lastConfirmed: Long?): Freshness = when {
        lastConfirmed == null -> Freshness.NEVER
        now - lastConfirmed < FRESH_WINDOW_MS -> Freshness.FRESH
        else -> Freshness.STALE
    }

    fun freshCount(checks: List<Check>, now: Long): Int =
        checks.count { freshness(now, it.lastConfirmed) == Freshness.FRESH }

    fun cleanName(raw: String): String = raw.trim().replace(Regex("\\s+"), " ").take(MAX_NAME_LENGTH)

    fun add(checks: List<Check>, name: String, emoji: String, id: String = newId()): List<Check> {
        val clean = cleanName(name)
        if (clean.isEmpty()) return checks
        return checks + Check(id, clean, emoji, emptyList())
    }

    fun confirm(checks: List<Check>, id: String, at: Long): List<Check> = checks.map {
        if (it.id == id) it.copy(history = (listOf(at) + it.history).take(MAX_HISTORY)) else it
    }

    /** Removes one specific confirmation (used by Undo). */
    fun removeConfirmation(checks: List<Check>, id: String, at: Long): List<Check> = checks.map { check ->
        if (check.id != id) return@map check
        val index = check.history.indexOf(at)
        if (index < 0) check else check.copy(history = check.history.filterIndexed { i, _ -> i != index })
    }

    fun rename(checks: List<Check>, id: String, name: String): List<Check> {
        val clean = cleanName(name)
        if (clean.isEmpty()) return checks
        return checks.map { if (it.id == id) it.copy(name = clean) else it }
    }

    fun clearHistory(checks: List<Check>, id: String): List<Check> =
        checks.map { if (it.id == id) it.copy(history = emptyList()) else it }

    fun delete(checks: List<Check>, id: String): List<Check> = checks.filterNot { it.id == id }
}
