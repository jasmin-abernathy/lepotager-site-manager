package org.lepotager.sitemanager.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.materialkolor.rememberDynamicColorScheme

private val PotagerGreen = Color(0xFF4D7C59)

@Composable
fun BrandTheme(
    primaryHex: String?,
    content: @Composable () -> Unit,
) {
    val seed = parseHexColor(primaryHex) ?: PotagerGreen
    val scheme = rememberDynamicColorScheme(
        seedColor = seed,
        isDark = isSystemInDarkTheme(),
        isAmoled = false,
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

private fun parseHexColor(value: String?): Color? {
    val hex = value?.trim()?.removePrefix("#") ?: return null
    if (hex.length != 6 && hex.length != 8) return null
    val raw = hex.toLongOrNull(16) ?: return null
    val alpha = if (hex.length == 8) ((raw shr 24) and 0xFF).toInt() else 0xFF
    val red = ((raw shr 16) and 0xFF).toInt()
    val green = ((raw shr 8) and 0xFF).toInt()
    val blue = (raw and 0xFF).toInt()
    return Color(red = red, green = green, blue = blue, alpha = alpha)
}
