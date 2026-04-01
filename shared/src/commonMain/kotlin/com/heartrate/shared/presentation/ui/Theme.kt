package com.heartrate.shared.presentation.ui

/**
 * Shared color definitions for the heart rate monitoring app.
 * These colors are used across all platforms for consistent branding.
 *
 * Note: These are data classes, not actual Compose colors,
 * as Compose UI cannot be shared in commonMain yet.
 * Each platform should convert these to platform-specific color objects.
 */
data class AppColors(
    val primary: Color,
    val primaryDark: Color,
    val accent: Color,
    val background: Color,
    val surface: Color,
    val error: Color,
    val onPrimary: Color,
    val onBackground: Color,
    val onSurface: Color,
    val onError: Color
) {
    companion object {
        /**
         * Default color scheme for the heart rate monitoring app.
         * Uses a health-focused green theme.
         */
        fun default() = AppColors(
            primary = Color(0xFF4CAF50u),        // Green 500
            primaryDark = Color(0xFF388E3Cu),    // Green 700
            accent = Color(0xFFFF9800u),         // Orange 500
            background = Color(0xFF121212u),     // Dark background
            surface = Color(0xFF1E1E1Eu),        // Dark surface
            error = Color(0xFFCF6679u),          // Error red
            onPrimary = Color(0xFFFFFFFFu),      // White
            onBackground = Color(0xFFE0E0E0u),   // Light gray
            onSurface = Color(0xFFE0E0E0u),      // Light gray
            onError = Color(0xFF000000u)         // Black
        )

        /**
         * Light theme variant.
         */
        fun light() = AppColors(
            primary = Color(0xFF4CAF50u),        // Green 500
            primaryDark = Color(0xFF388E3Cu),    // Green 700
            accent = Color(0xFFFF9800u),         // Orange 500
            background = Color(0xFFFAFAFAu),     // Light background
            surface = Color(0xFFFFFFFFu),        // White surface
            error = Color(0xFFB00020u),          // Error red
            onPrimary = Color(0xFFFFFFFFu),      // White
            onBackground = Color(0xFF121212u),   // Dark gray
            onSurface = Color(0xFF121212u),      // Dark gray
            onError = Color(0xFFFFFFFFu)         // White
        )
    }
}

/**
 * Represents a color in ARGB format.
 * Platform-specific implementations should convert this to native color objects.
 */
data class Color(val value: ULong) {
    companion object {
        fun fromRgb(red: Int, green: Int, blue: Int): Color {
            return Color(
                (0xFFUL shl 24) or
                (red.toULong() shl 16) or
                (green.toULong() shl 8) or
                blue.toULong()
            )
        }
    }

    val red: Int
        get() = ((value shr 16) and 0xFFUL).toInt()

    val green: Int
        get() = ((value shr 8) and 0xFFUL).toInt()

    val blue: Int
        get() = (value and 0xFFUL).toInt()

    val alpha: Int
        get() = ((value shr 24) and 0xFFUL).toInt()
}

/**
 * Text style definitions for consistent typography across platforms.
 */
data class AppTextStyle(
    val fontSize: TextUnit,
    val fontWeight: FontWeight,
    val letterSpacing: TextUnit = TextUnit.Unspecified
)

/**
 * Font weight enumeration.
 */
enum class FontWeight {
    Normal,
    Medium,
    SemiBold,
    Bold
}

/**
 * Text unit enumeration.
 */
sealed class TextUnit {
    object Unspecified : TextUnit()
    data class Sp(val value: Float) : TextUnit()
    data class Dp(val value: Float) : TextUnit()
    data class Em(val value: Float) : TextUnit()
}

/**
 * Typography definitions for the app.
 */
data class AppTypography(
    val h1: AppTextStyle,
    val h2: AppTextStyle,
    val h3: AppTextStyle,
    val bodyLarge: AppTextStyle,
    val bodyMedium: AppTextStyle,
    val bodySmall: AppTextStyle,
    val label: AppTextStyle
) {
    companion object {
        fun default() = AppTypography(
            h1 = AppTextStyle(TextUnit.Sp(32.0f), FontWeight.Bold),
            h2 = AppTextStyle(TextUnit.Sp(24.0f), FontWeight.SemiBold),
            h3 = AppTextStyle(TextUnit.Sp(20.0f), FontWeight.SemiBold),
            bodyLarge = AppTextStyle(TextUnit.Sp(16.0f), FontWeight.Normal),
            bodyMedium = AppTextStyle(TextUnit.Sp(14.0f), FontWeight.Normal),
            bodySmall = AppTextStyle(TextUnit.Sp(12.0f), FontWeight.Normal),
            label = AppTextStyle(TextUnit.Sp(14.0f), FontWeight.Medium, TextUnit.Sp(0.5f))
        )
    }
}

/**
 * Spacing constants for consistent layout across platforms.
 */
data class AppSpacing(
    val xs: Dp,
    val sm: Dp,
    val md: Dp,
    val lg: Dp,
    val xl: Dp,
    val xxl: Dp
) {
    companion object {
        fun default() = AppSpacing(
            xs = Dp(4f),
            sm = Dp(8f),
            md = Dp(16f),
            lg = Dp(24f),
            xl = Dp(32f),
            xxl = Dp(48f)
        )
    }
}

/**
 * Device-independent pixels.
 */
data class Dp(val value: Float)

/**
 * Complete theme definition combining colors, typography, and spacing.
 */
data class AppTheme(
    val colors: AppColors,
    val typography: AppTypography,
    val spacing: AppSpacing
) {
    companion object {
        fun default() = AppTheme(
            colors = AppColors.default(),
            typography = AppTypography.default(),
            spacing = AppSpacing.default()
        )

        fun light() = AppTheme(
            colors = AppColors.light(),
            typography = AppTypography.default(),
            spacing = AppSpacing.default()
        )
    }
}
