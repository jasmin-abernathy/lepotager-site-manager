package org.lepotager.sitemanager.ui

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal actual fun formatBusinessDateTime(raw: String): String {
    val formatter = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.getDefault())
    runCatching { return OffsetDateTime.parse(raw).format(formatter) }
    runCatching { return LocalDateTime.parse(raw).format(formatter) }
    runCatching {
        return LocalDate.parse(raw).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
    }
    return raw.replace('T', ' ').substringBeforeLast(':').ifBlank { raw }
}
