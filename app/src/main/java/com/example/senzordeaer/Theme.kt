package com.example.senzordeaer

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF00696D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF6FF6FA),
    onPrimaryContainer = Color(0xFF002020),
    secondary = Color(0xFF4A6363),
    background = Color(0xFFFAFDFC),
    onBackground = Color(0xFF191C1C),
    surface = Color(0xFFFAFDFC),
    onSurface = Color(0xFF191C1C),
    surfaceVariant = Color(0xFFDAE5E4),
    error = Color(0xFFBA1A1A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4DD9DE),
    onPrimary = Color(0xFF003738),
    primaryContainer = Color(0xFF004F51),
    onPrimaryContainer = Color(0xFF6FF6FA),
    secondary = Color(0xFFB1CCCC),
    background = Color(0xFF191C1C),
    onBackground = Color(0xFFE0E3E2),
    surface = Color(0xFF191C1C),
    onSurface = Color(0xFFE0E3E2),
    surfaceVariant = Color(0xFF3F4948),
    error = Color(0xFFFFB4AB)
)

/** Preferință de temă (light/dark) persistată local; independentă de sesiunea securizată. */
class ThemePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)

    fun getIsDarkMode(systemDefault: Boolean): Boolean =
        prefs.getBoolean("dark_mode_enabled", systemDefault)

    fun setIsDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean("dark_mode_enabled", enabled).apply()
    }
}

/** Temă Material 3 cu tranziție animată a culorilor la comutarea light/dark. */
@Composable
fun SenzorDeAerTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val target = if (darkTheme) DarkColors else LightColors
    val animSpec = tween<Color>(durationMillis = 350)

    val animatedScheme = target.copy(
        primary = animateColorAsState(target.primary, animSpec, label = "primary").value,
        onPrimary = animateColorAsState(target.onPrimary, animSpec, label = "onPrimary").value,
        primaryContainer = animateColorAsState(target.primaryContainer, animSpec, label = "primaryContainer").value,
        onPrimaryContainer = animateColorAsState(target.onPrimaryContainer, animSpec, label = "onPrimaryContainer").value,
        secondary = animateColorAsState(target.secondary, animSpec, label = "secondary").value,
        background = animateColorAsState(target.background, animSpec, label = "background").value,
        onBackground = animateColorAsState(target.onBackground, animSpec, label = "onBackground").value,
        surface = animateColorAsState(target.surface, animSpec, label = "surface").value,
        onSurface = animateColorAsState(target.onSurface, animSpec, label = "onSurface").value,
        surfaceVariant = animateColorAsState(target.surfaceVariant, animSpec, label = "surfaceVariant").value,
        error = animateColorAsState(target.error, animSpec, label = "error").value
    )

    MaterialTheme(colorScheme = animatedScheme, content = content)
}
