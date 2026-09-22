package org.lepotager.sitemanager.ui

/**
 * Fallback iOS sans dépendance Foundation tant que la cible n’a pas été validée
 * sous Xcode. Préserve une date ISO lisible au lieu de masquer la valeur.
 */
internal actual fun formatBusinessDateTime(raw: String): String {
    val normalized = raw.trim()
    if (normalized.isBlank()) return raw
    val date = normalized.substringBefore('T')
    val afterT = normalized.substringAfter('T', "")
    val time = afterT.take(5).takeIf { it.length == 5 && it[2] == ':' }
    return if (time != null) "$date · $time" else date.ifBlank { raw }
}
