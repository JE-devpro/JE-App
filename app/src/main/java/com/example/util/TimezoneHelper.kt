package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object TimezoneHelper {
    private const val EC_TIMEZONE_ID = "America/Guayaquil"

    /**
     * Parses an activity date string (which is relative to America/Guayaquil / Ecuador time)
     * and returns the absolute UTC epoch milliseconds.
     */
    fun parseEcuadorDateTime(dateStr: String): Long? {
        val formats = listOf(
            "yyyy-MM-dd HH:mm",
            "yyyy-M-d H:m",
            "yyyy-MM-dd",
            "yyyy-M-d",
            "yyyy/MM/dd HH:mm",
            "yyyy/M/d H:m",
            "yyyy/MM/dd",
            "yyyy/M/d"
        )
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone(EC_TIMEZONE_ID)
                }
                val parsed = sdf.parse(dateStr.trim())
                if (parsed != null) {
                    return parsed.time
                }
            } catch (e: Exception) {
                // Try next
            }
        }
        return null
    }

    /**
     * Converts a stored Ecuador datetime string (e.g. "2026-06-15 22:30") to the corresponding
     * local timezone date and time strings.
     */
    fun ecuadorToLocalParts(ecuadorDateStr: String): Pair<String, String> {
        val trimmed = ecuadorDateStr.trim()
        val epochMs = parseEcuadorDateTime(trimmed) ?: return Pair(
            trimmed.substringBefore(" "),
            if (trimmed.contains(" ")) trimmed.substringAfter(" ") else "10:00"
        )
        
        try {
            val localDateSdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
                timeZone = TimeZone.getDefault()
            }
            val localTimeSdf = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
                timeZone = TimeZone.getDefault()
            }
            val d = Date(epochMs)
            return Pair(localDateSdf.format(d), localTimeSdf.format(d))
        } catch (e: Exception) {
            return Pair(
                trimmed.substringBefore(" "),
                if (trimmed.contains(" ")) trimmed.substringAfter(" ") else "10:00"
            )
        }
    }

    /**
     * Converts a local date ("yyyy-MM-dd") and time ("HH:mm") back to the Ecuador timezone
     * string ("yyyy-MM-dd HH:mm") to be securely stored.
     */
    fun localPartsToEcuadorStr(localDate: String, localTime: String): String {
        try {
            val localSdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).apply {
                timeZone = TimeZone.getDefault()
            }
            val parsed = localSdf.parse("${localDate.trim()} ${localTime.trim()}")
            if (parsed != null) {
                val ecSdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone(EC_TIMEZONE_ID)
                }
                return ecSdf.format(parsed)
            }
        } catch (e: Exception) {
            // ignore
        }
        return "${localDate.trim()} ${localTime.trim()}"
    }

    /**
     * Returns true if the device's local timezone has the same offset as Ecuador (GMT-5)
     * at the given time, or if the timezone ID matches.
     */
    fun isDeviceInEcuadorTimezone(timeMs: Long = System.currentTimeMillis()): Boolean {
        val ecZone = TimeZone.getTimeZone(EC_TIMEZONE_ID)
        val localZone = TimeZone.getDefault()
        if (ecZone.id == localZone.id) return true
        
        return ecZone.getOffset(timeMs) == localZone.getOffset(timeMs)
    }

    /**
     * Formats an activity's Ecuador datetime string to a human-readable representation
     * that includes local equivalent time if the user is in a different timezone.
     */
    fun formatActivityDate(dateStr: String): String {
        val trimmed = dateStr.trim()
        val hasTime = trimmed.contains(" ")

        // Parse using Ecuador timezone
        val epochMs = parseEcuadorDateTime(trimmed) ?: return trimmed

        if (!hasTime) {
            // No time component, just show raw date
            return trimmed
        }

        // If user is in Ecuador (or same offset), just return the raw date/time string.
        if (isDeviceInEcuadorTimezone(epochMs)) {
            return trimmed
        }

        // Otherwise, format the same point in time in device's local timezone
        try {
            val localSdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).apply {
                timeZone = TimeZone.getDefault()
            }
            val localFormatted = localSdf.format(Date(epochMs))
            
            // Get short local timezone name
            val localZoneName = TimeZone.getDefault().getDisplayName(TimeZone.getDefault().inDaylightTime(Date(epochMs)), TimeZone.SHORT, Locale.getDefault())

            return "$trimmed (Ecuador) | $localFormatted ($localZoneName)"
        } catch (e: Exception) {
            return trimmed
        }
    }
}
