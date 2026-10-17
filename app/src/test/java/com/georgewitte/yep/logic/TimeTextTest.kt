package com.georgewitte.yep.logic

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class TimeTextTest {
    private val minute = 60_000L
    private val zone = ZoneId.of("America/New_York")
    private val locale = Locale.US

    private fun millis(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun agoText() {
        assertEquals("just now", TimeText.ago(10 * minute, 10 * minute))
        assertEquals("just now", TimeText.ago(0, 5_000)) // clock skew never goes negative
        assertEquals("5 min ago", TimeText.ago(5 * minute, 0))
        assertEquals("1 hr ago", TimeText.ago(60 * minute, 0))
        assertEquals("2 hr 13 min ago", TimeText.ago(133 * minute, 0))
        assertEquals("7 hr ago", TimeText.ago(7 * 60 * minute + 40 * minute, 0))
        assertEquals("1 day ago", TimeText.ago(25 * 60 * minute, 0))
        assertEquals("3 days ago", TimeText.ago(3 * 24 * 60 * minute, 0))
    }

    @Test
    fun dayLabels() {
        val today = LocalDate.of(2026, 10, 17) // a Saturday
        assertEquals("Today", TimeText.dayLabel(today, today, locale))
        assertEquals("Yesterday", TimeText.dayLabel(today, today.minusDays(1), locale))
        assertEquals("Tuesday", TimeText.dayLabel(today, today.minusDays(4), locale))
        assertEquals("Oct 3", TimeText.dayLabel(today, LocalDate.of(2026, 10, 3), locale))
        assertEquals("Dec 30, 2025", TimeText.dayLabel(today, LocalDate.of(2025, 12, 30), locale))
    }

    @Test
    fun statusLine() {
        val now = millis("2026-10-17T22:55:00")
        val then = millis("2026-10-17T20:42:00")
        val status = TimeText.status(now, then, zone, locale) { "8:42 PM" }
        assertEquals("Today, 8:42 PM · 2 hr 13 min ago", status)

        val lastNight = millis("2026-10-16T23:30:00")
        assertEquals("Yesterday, 11:30 PM", TimeText.moment(now, lastNight, zone, locale) { "11:30 PM" })
    }
}
