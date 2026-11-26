package com.fieldtrack.app.core.designsystem

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.fieldtrack.app.R

// ─── FieldTrack Typography ────────────────────────────────────────────────────
// Headlines: Lexend (expanded width, max legibility)
// Body/UI:   Plus Jakarta Sans (clean, modern)
//
// Google Fonts downloads asynchronously — system sans-serif is used until loaded.
// No crash on offline / no-GMS: Compose falls back to system fonts gracefully.
// ─────────────────────────────────────────────────────────────────────────────

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage   = "com.google.android.gms",
    certificates      = R.array.com_google_android_gms_fonts_certs,
)

val LexendFont          = GoogleFont("Lexend")
val PlusJakartaSansFont = GoogleFont("Plus Jakarta Sans")

val LexendFamily = FontFamily(
    Font(googleFont = LexendFont, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = LexendFont, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = LexendFont, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = LexendFont, fontProvider = provider, weight = FontWeight.Bold),
    Font(googleFont = LexendFont, fontProvider = provider, weight = FontWeight.ExtraBold),
)

val PlusJakartaFamily = FontFamily(
    Font(googleFont = PlusJakartaSansFont, fontProvider = provider, weight = FontWeight.Light),
    Font(googleFont = PlusJakartaSansFont, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = PlusJakartaSansFont, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = PlusJakartaSansFont, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = PlusJakartaSansFont, fontProvider = provider, weight = FontWeight.Bold),
    Font(googleFont = PlusJakartaSansFont, fontProvider = provider, weight = FontWeight.ExtraBold),
)

// ─── Type Scale ───────────────────────────────────────────────────────────────

object FtTypography {
    // Display / Headlines — Lexend
    val H1 = TextStyle(
        fontFamily    = LexendFamily,
        fontWeight    = FontWeight.Bold,
        fontSize      = 32.sp,
        lineHeight    = 40.sp,
        letterSpacing = (-0.64).sp,
    )
    val H2 = TextStyle(
        fontFamily    = LexendFamily,
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 24.sp,
        lineHeight    = 32.sp,
        letterSpacing = (-0.24).sp,
    )
    val H3 = TextStyle(
        fontFamily = LexendFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 20.sp,
        lineHeight = 28.sp,
    )
    val H4 = TextStyle(
        fontFamily = LexendFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 16.sp,
        lineHeight = 24.sp,
    )

    // Body — Plus Jakarta Sans
    val BodyLarge = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.Normal,
        fontSize   = 16.sp,
        lineHeight = 24.sp,
    )
    val BodyMedium = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.Normal,
        fontSize   = 14.sp,
        lineHeight = 20.sp,
    )
    val BodySmall = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.Normal,
        fontSize   = 12.sp,
        lineHeight = 18.sp,
    )

    // Labels — Plus Jakarta Sans
    val LabelLarge = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 14.sp,
        lineHeight = 20.sp,
    )
    val LabelMedium = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 12.sp,
        lineHeight = 16.sp,
    )
    val LabelSmall = TextStyle(
        fontFamily    = PlusJakartaFamily,
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 10.sp,
        lineHeight    = 14.sp,
        letterSpacing = 0.08.sp,
    )

    // Button
    val Button = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 14.sp,
        lineHeight = 20.sp,
    )
}
