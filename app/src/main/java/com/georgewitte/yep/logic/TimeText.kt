package com.georgewitte.yep.logic

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Human-friendly time text. Pure functions so they can be unit tested on the JVM. */
object TimeText {
    private const val MINUTE = 60_000L

    /** "just now", "5 min ago", "2 hr 10 min ago", "7 hr ago", "3 days ago". */
    fun ago(now: Long, then: Long): String {
        val minutes = (now - then).coerceAtLeast(0) / MINUTE
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            minutes < 24 * 60 -> {
                val hours = minutes / 60
                val rest = minutes % 60
                if (hours < 3 && rest > 0) "$hours hr $rest min ago" else "$hours hr ago"
            }
            else -> {
                val days = minutes / (24 * 60)
                if (days == 1L) "1 day ago" else "$days days ago"
            }
        }
    }

    /** "Today", "Yesterday", "Monday" (within a week), "Mar 3", or "Mar 3, 2025". */
    fun dayLabel(today: LocalDate, day: LocalDate, locale: Locale): String {
        val daysBack = ChronoUnit.DAYS.between(day, today)
        return when {
            daysBack == 0L -> "Today"
            daysBack == 1L -> "Yesterday"
            daysBack in 2..6 -> day.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
            day.year == today.year -> day.format(DateTimeFormatter.ofPattern("MMM d", locale))
            else -> day.format(DateTimeFormatter.ofPattern("MMM d, yyyy", locale))
        }
    }

    /** "Today, 8:42 PM". [formatTime] renders the clock time in the user's 12/24-hour preference. */
    fun moment(now: Long, then: Long, zone: ZoneId, locale: Locale, formatTime: (Long) -> String): String {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val day = Instant.ofEpochMilli(then).atZone(zone).toLocalDate()
        return "${dayLabel(today, day, locale)}, ${formatTime(then)}"
    }

    /** "Today, 8:42 PM · 2 hr ago" */
    fun status(now: Long, then: Long, zone: ZoneId, locale: Locale, formatTime: (Long) -> String): String =
        "${moment(now, then, zone, locale, formatTime)} · ${ago(now, then)}"
}
