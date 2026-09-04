package org.lepotager.sitemanager.ui

import android.graphics.Color as AndroidColor
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
    val seed = runCatching {
        primaryHex?.let { Color(AndroidColor.parseColor(it)) }
    }.getOrNull() ?: PotagerGreen
    val scheme = rememberDynamicColorScheme(
        seedColor = seed,
        isDark = isSystemInDarkTheme(),
        animate = true,
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
