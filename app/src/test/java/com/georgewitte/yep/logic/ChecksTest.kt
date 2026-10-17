package com.georgewitte.yep.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChecksTest {
    private val hour = 60L * 60 * 1000
    private fun sample() = listOf(
        Check("a", "Front door locked", "🔒", emptyList()),
        Check("b", "Stove off", "🔥", listOf(5_000L, 1_000L)),
    )

    @Test
    fun confirmPutsNewestFirst() {
        val result = Checks.confirm(sample(), "b", 9_000L)
        assertEquals(listOf(9_000L, 5_000L, 1_000L), result[1].history)
        assertEquals(9_000L, result[1].lastConfirmed)
        assertEquals(emptyList<Long>(), result[0].history)
    }

    @Test
    fun historyIsCapped() {
        var checks = sample()
        repeat(Checks.MAX_HISTORY + 10) { checks = Checks.confirm(checks, "a", it.toLong()) }
        assertEquals(Checks.MAX_HISTORY, checks[0].history.size)
        assertEquals((Checks.MAX_HISTORY + 9).toLong(), checks[0].lastConfirmed)
    }

    @Test
    fun undoRemovesOnlyThatConfirmation() {
        val confirmed = Checks.confirm(sample(), "b", 9_000L)
        val undone = Checks.removeConfirmation(confirmed, "b", 9_000L)
        assertEquals(listOf(5_000L, 1_000L), undone[1].history)
        // Unknown timestamp leaves the list alone.
        assertEquals(confirmed[1], Checks.removeConfirmation(confirmed, "b", 42L)[1])
    }

    @Test
    fun freshnessWindow() {
        val now = 100 * hour
        assertEquals(Freshness.NEVER, Checks.freshness(now, null))
        assertEquals(Freshness.FRESH, Checks.freshness(now, now - 11 * hour))
        assertEquals(Freshness.STALE, Checks.freshness(now, now - 12 * hour))
        val checks = Checks.confirm(Checks.confirm(sample(), "a", now - hour), "b", now - 20 * hour)
        assertEquals(1, Checks.freshCount(checks, now))
    }

    @Test
    fun addTrimsAndRejectsBlankNames() {
        val added = Checks.add(sample(), "   Garage   door  ", "🚪", id = "c")
        assertEquals(Check("c", "Garage door", "🚪", emptyList()), added.last())
        assertEquals(sample().size, Checks.add(sample(), "   ", "🚪").size)
        assertEquals(Checks.MAX_NAME_LENGTH, Checks.cleanName("x".repeat(100)).length)
    }

    @Test
    fun renameClearAndDelete() {
        val renamed = Checks.rename(sample(), "a", "Back door locked")
        assertEquals("Back door locked", renamed[0].name)
        assertEquals("Front door locked", Checks.rename(sample(), "a", " ")[0].name)
        assertNull(Checks.clearHistory(sample(), "b")[1].lastConfirmed)
        assertEquals(listOf("b"), Checks.delete(sample(), "a").map { it.id })
    }

    @Test
    fun defaultsHaveUniqueIds() {
        var n = 0
        val defaults = Checks.defaults { "id${n++}" }
        assertEquals(4, defaults.size)
        assertEquals(defaults.size, defaults.map { it.id }.toSet().size)
    }
}
