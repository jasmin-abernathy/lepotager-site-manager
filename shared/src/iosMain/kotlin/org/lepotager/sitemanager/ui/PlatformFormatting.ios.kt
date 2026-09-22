package org.lepotager.sitemanager.ui

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.localeWithLocaleIdentifier

private val businessDateInput = NSDateFormatter().apply {
    locale = NSLocale.localeWithLocaleIdentifier("en_US_POSIX")
    dateFormat = "yyyy-MM-dd"
}

private val businessMinuteInput = NSDateFormatter().apply {
    locale = NSLocale.localeWithLocaleIdentifier("en_US_POSIX")
    dateFormat = "yyyy-MM-dd'T'HH:mm"
}

private val businessSecondInput = NSDateFormatter().apply {
    locale = NSLocale.localeWithLocaleIdentifier("en_US_POSIX")
    dateFormat = "yyyy-MM-dd'T'HH:mm:ss"
}

private val businessDateOutput = NSDateFormatter().apply {
    locale = NSLocale.currentLocale
    dateFormat = "EEE d MMM"
}

private val businessDateTimeOutput = NSDateFormatter().apply {
    locale = NSLocale.currentLocale
    dateFormat = "EEE d MMM · HH:mm"
}

internal actual fun formatBusinessDateTime(raw: String): String {
    val normalized = raw.trim()
    if (normalized.isBlank()) return raw

    val hasTime = 'T' in normalized
    val date = parseBusinessDate(normalized) ?: return raw.replace('T', ' ').substringBeforeLast(':').ifBlank { raw }
    return if (hasTime) businessDateTimeOutput.stringFromDate(date) else businessDateOutput.stringFromDate(date)
}

private fun parseBusinessDate(raw: String): NSDate? = when {
    'T' !in raw -> businessDateInput.dateFromString(raw.take(10))
    raw.length >= 19 -> businessSecondInput.dateFromString(raw.take(19))
    raw.length >= 16 -> businessMinuteInput.dateFromString(raw.take(16))
    else -> null
}
