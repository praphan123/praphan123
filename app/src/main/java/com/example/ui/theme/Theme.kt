package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class HazardSemanticColors(
    val critical: Color,
    val criticalContainer: Color,
    val high: Color,
    val moderate: Color,
    val moderateContainer: Color,
    val low: Color,
    val lowContainer: Color,
    val telemetry: Color,
    val telemetryContainer: Color,
    val elevatedPanel: Color
)

val LocalHazardColors = staticCompositionLocalOf {
    HazardSemanticColors(
        critical = CriticalCoral,
        criticalContainer = CriticalCoralContainer,
        high = HighOrange,
        moderate = AlertAmber,
        moderateContainer = AlertAmberContainer,
        low = SafeEmerald,
        lowContainer = SafeEmeraldContainer,
        telemetry = TelemetryCyan,
        telemetryContainer = TelemetryCyanContainer,
        elevatedPanel = CommandSurfaceHigh
    )
}

private val TacticalDarkColorScheme = darkColorScheme(
    primary = AlertAmber,
    onPrimary = Color(0xFF1F1300),
    primaryContainer = AlertAmberContainer,
    onPrimaryContainer = AlertAmberOnContainer,
    secondary = TelemetryCyan,
    onSecondary = Color(0xFF001E24),
    secondaryContainer = TelemetryCyanContainer,
    onSecondaryContainer = TelemetryCyanOnContainer,
    tertiary = SafeEmerald,
    onTertiary = Color(0xFF002116),
    tertiaryContainer = SafeEmeraldContainer,
    onTertiaryContainer = SafeEmeraldOnContainer,
    error = CriticalCoral,
    onError = Color.White,
    errorContainer = CriticalCoralContainer,
    onErrorContainer = CriticalCoralOnContainer,
    background = CommandObsidian,
    onBackground = Color(0xFFF1F5F9),
    surface = CommandNavy,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = CommandSurface,
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainerLow = CommandNavy,
    surfaceContainer = CommandSurface,
    surfaceContainerHigh = CommandSurfaceHigh,
    outline = CommandOutline
)

private val TacticalLightColorScheme = lightColorScheme(
    primary = LightAmberPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFEF3C7),
    onPrimaryContainer = Color(0xFF451A03),
    secondary = LightCyanSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFFAFE),
    onSecondaryContainer = Color(0xFF083344),
    tertiary = LightSafe,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD1FAE5),
    onTertiaryContainer = Color(0xFF022C22),
    error = LightCritical,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF450A0A),
    background = LightFrostBg,
    onBackground = LightSlateText,
    surface = LightFrostSurface,
    onSurface = LightSlateText,
    surfaceVariant = LightFrostSurfaceVariant,
    onSurfaceVariant = LightMutedText,
    surfaceContainerLow = LightFrostSurface,
    surfaceContainer = Color(0xFFEEF2F8),
    surfaceContainerHigh = Color(0xFFE2E8F0),
    outline = LightOutline
)

private val DisasterShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun DisasterPulseTheme(
    darkTheme: Boolean = true,
    useSystemTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDark = if (useSystemTheme) isSystemInDarkTheme() else darkTheme
    val colorScheme = if (isDark) TacticalDarkColorScheme else TacticalLightColorScheme
    val hazardColors = if (isDark) {
        HazardSemanticColors(
            critical = CriticalCoral,
            criticalContainer = CriticalCoralContainer,
            high = HighOrange,
            moderate = AlertAmber,
            moderateContainer = AlertAmberContainer,
            low = SafeEmerald,
            lowContainer = SafeEmeraldContainer,
            telemetry = TelemetryCyan,
            telemetryContainer = TelemetryCyanContainer,
            elevatedPanel = CommandSurfaceHigh
        )
    } else {
        HazardSemanticColors(
            critical = LightCritical,
            criticalContainer = Color(0xFFFEE2E2),
            high = LightHighOrange,
            moderate = LightAmberPrimary,
            moderateContainer = Color(0xFFFEF3C7),
            low = LightSafe,
            lowContainer = Color(0xFFD1FAE5),
            telemetry = LightCyanSecondary,
            telemetryContainer = Color(0xFFCFFAFE),
            elevatedPanel = Color(0xFFE2E8F0)
        )
    }

    CompositionLocalProvider(LocalHazardColors provides hazardColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = DisasterTypography,
            shapes = DisasterShapes,
            content = content
        )
    }
}
