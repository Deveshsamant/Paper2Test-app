package app.paper2test.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.paper2test.R

/** Paper2Test design tokens (same as the website's style.css, from the Stitch redesign), light and dark. */
@Immutable
data class P2TPalette(
    val isDark: Boolean, val brand: Color, val brand2: Color, val indigo: Color, val canvas: Color, val card: Color,
    val tint: Color, val tint2: Color, val tint3: Color, val ink: Color, val ink2: Color, val muted: Color, val line: Color,
    val ok: Color, val okInk: Color, val bad: Color, val warn: Color, val okBg: Color, val okLine: Color, val badBg: Color,
    val badLine: Color, val warnBg: Color, val warnInk: Color, val slateBg: Color, val vioBg: Color, val onTint: Color,
)
val LightPalette = P2TPalette(
    isDark = false, brand = Color(0xFF004AC6), brand2 = Color(0xFF2563EB), indigo = Color(0xFF4B41E1), canvas = Color(0xFFF8FAFC), card = Color.White,
    tint = Color(0xFFEFF4FF), tint2 = Color(0xFFE5EEFF), tint3 = Color(0xFFDCE9FF), ink = Color(0xFF0F172A), ink2 = Color(0xFF434655), muted = Color(0xFF64748B), line = Color(0xFFE2E8F0),
    ok = Color(0xFF10B981), okInk = Color(0xFF047857), bad = Color(0xFFBA1A1A), warn = Color(0xFFF59E0B), okBg = Color(0xFFECFDF5), okLine = Color(0xFFA7F3D0), badBg = Color(0xFFFEF2F2),
    badLine = Color(0xFFFECACA), warnBg = Color(0xFFFFFBEB), warnInk = Color(0xFFB45309), slateBg = Color(0xFFF1F5F9), vioBg = Color(0xFFEEF2FF), onTint = Color(0xFF004AC6),
)
val DarkPalette = P2TPalette(
    isDark = true, brand = Color(0xFF4D8DFF), brand2 = Color(0xFF3B82F6), indigo = Color(0xFF8B83FF), canvas = Color(0xFF0B1120), card = Color(0xFF111A2E),
    tint = Color(0xFF14203A), tint2 = Color(0xFF1A2947), tint3 = Color(0xFF233458), ink = Color(0xFFE6EDF7), ink2 = Color(0xFFB9C3D6), muted = Color(0xFF8A97AE), line = Color(0xFF1E2A44),
    ok = Color(0xFF10B981), okInk = Color(0xFF34D399), bad = Color(0xFFF87171), warn = Color(0xFFF59E0B), okBg = Color(0x1F10B981), okLine = Color(0x5910B981), badBg = Color(0x1FEF4444),
    badLine = Color(0x59EF4444), warnBg = Color(0x1FF59E0B), warnInk = Color(0xFFFBBF24), slateBg = Color(0xFF172238), vioBg = Color(0x248B83FF), onTint = Color(0xFF9CC0FF),
)
val LocalP2T = staticCompositionLocalOf { LightPalette }

object P2T {
    val isDark: Boolean @Composable @ReadOnlyComposable get() = LocalP2T.current.isDark
    val Brand: Color @Composable @ReadOnlyComposable get() = LocalP2T.current.brand
    val Brand2: Color @Composable @ReadOnlyComposable get() = LocalP2T.current.brand2
    val Indigo: Color @Composable @ReadOnlyComposable get() = LocalP2T.current.indigo
    val Canvas: Color @Composable @ReadOnlyComposable get() = LocalP2T.current.canvas
    /** Card / bar / sheet surface (white in light). */
    val Card: Color @Composable @ReadOnlyComposable get() = LocalP2T.current.card
    val Tint: Color @Composable @ReadOnlyComposable get() = LocalP2T.current.tint
    val Tint2: Color @Composable @ReadOnlyComposable get() = LocalP2T.current.tint2
    val Tint3: Color @Composable @ReadOnlyComposable get() = LocalP2T.current.tint3
    val Ink: Color @Composable @ReadOnlyComposable get() = LocalP2T.current.ink
    val Ink2: Color @Composable @ReadOnlyComposable get() = LocalP2T.current.ink2
    val Muted: Color @Composable @ReadOnlyComposable get() = LocalP2T.current.muted
    val Line: Color @Composable @ReadOnlyComposable get() = LocalP2T.current.line
    val Ok: Color @Composable @ReadOnlyComposable get() = LocalP2T.current.ok
    val OkInk: Color @Composable @ReadOnlyComposable get() = LocalP2T.current.okInk
    val Bad: Color @Composable @ReadOnlyComposable get() = LocalP2T.current.bad
    val Warn: Color @Composable @ReadOnlyComposable get() = LocalP2T.current.warn
    val OkBg: Color @Composable @ReadOnlyComposable get() = LocalP2T.current.okBg
    val OkLine: Color @Composable @ReadOnlyComposable get() = LocalP2T.current.okLine
    val BadBg: Color @Composable @ReadOnlyComposable get() = LocalP2T.current.badBg
    val BadLine: Color @Composable @ReadOnlyComposable get() = LocalP2T.current.badLine
    val WarnBg: Color @Composable @ReadOnlyComposable get() = LocalP2T.current.warnBg
    val WarnInk: Color @Composable @ReadOnlyComposable get() = LocalP2T.current.warnInk
    val SlateBg: Color @Composable @ReadOnlyComposable get() = LocalP2T.current.slateBg
    val VioBg: Color @Composable @ReadOnlyComposable get() = LocalP2T.current.vioBg
    /** Brand text on a tint background (brighter in dark). */
    val OnTint: Color @Composable @ReadOnlyComposable get() = LocalP2T.current.onTint
    val Hero = Brush.linearGradient(listOf(Color(0xFF0B3FB0), Color(0xFF2563EB), Color(0xFF4B41E1)))
}

val Jakarta = FontFamily(
    Font(R.font.jakarta_600, FontWeight.SemiBold),
    Font(R.font.jakarta_700, FontWeight.Bold),
    Font(R.font.jakarta_800, FontWeight.ExtraBold),
)

private fun scheme(p: P2TPalette) = if (!p.isDark) lightColorScheme(
    primary = p.brand, onPrimary = Color.White, primaryContainer = p.tint2, onPrimaryContainer = p.brand,
    secondary = p.indigo, onSecondary = Color.White, secondaryContainer = Color(0xFFE2DFFF), onSecondaryContainer = Color(0xFF3323CC),
    tertiary = p.okInk, tertiaryContainer = p.okBg, onTertiaryContainer = p.okInk,
    background = p.canvas, onBackground = p.ink, surface = p.canvas, onSurface = p.ink, onSurfaceVariant = p.ink2,
    surfaceVariant = p.tint, surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White, surfaceContainer = p.tint,
    surfaceContainerHigh = p.tint2, surfaceContainerHighest = p.tint3, outline = Color(0xFF737686), outlineVariant = p.line,
    error = p.bad, errorContainer = Color(0xFFFFDAD6),
) else darkColorScheme(
    primary = p.brand, onPrimary = Color.White, primaryContainer = p.tint2, onPrimaryContainer = p.onTint,
    secondary = p.indigo, onSecondary = Color.White, secondaryContainer = p.vioBg, onSecondaryContainer = Color(0xFFC7C2FF),
    tertiary = p.okInk, tertiaryContainer = p.okBg, onTertiaryContainer = p.okInk,
    background = p.canvas, onBackground = p.ink, surface = p.canvas, onSurface = p.ink, onSurfaceVariant = p.ink2,
    surfaceVariant = p.tint, surfaceContainerLowest = p.card, surfaceContainerLow = p.card, surfaceContainer = p.tint,
    surfaceContainerHigh = p.tint2, surfaceContainerHighest = p.tint3, outline = Color(0xFF5B6A88), outlineVariant = p.line,
    error = p.bad, errorContainer = p.badBg,
)

private val Base = Typography()
private fun TextStyle.head(size: Int, w: FontWeight = FontWeight.Bold, ls: Double = -0.015) = copy(fontFamily = Jakarta, fontWeight = w, fontSize = size.sp, letterSpacing = (size * ls).sp)
private val Type = Typography(
    displaySmall = Base.displaySmall.head(34, FontWeight.ExtraBold, -0.03),
    headlineLarge = Base.headlineLarge.head(30, FontWeight.ExtraBold, -0.025),
    headlineMedium = Base.headlineMedium.head(26, FontWeight.ExtraBold, -0.02),
    headlineSmall = Base.headlineSmall.head(22),
    titleLarge = Base.titleLarge.head(20),
    titleMedium = Base.titleMedium.head(16, FontWeight.Bold, 0.0),
    titleSmall = Base.titleSmall.head(14, FontWeight.SemiBold, 0.0),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

@Composable
fun P2TTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val p = if (dark) DarkPalette else LightPalette
    CompositionLocalProvider(LocalP2T provides p) {
        MaterialTheme(colorScheme = scheme(p), typography = Type, shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(18.dp)), content = content)
    }
}

/** White card with a hairline border and a soft shadow (the website's .card). */
@Composable
fun P2TCard(modifier: Modifier = Modifier, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.fillMaxWidth().shadow(6.dp, shape, ambientColor = Color(0x140F172A), spotColor = Color(0x140F172A)).clip(shape).background(P2T.Card).border(1.dp, P2T.Line, shape).padding(padding),
        verticalArrangement = Arrangement.spacedBy(10.dp), content = content,
    )
}

/** Blue → indigo gradient panel (join card, hero). */
@Composable
fun GradientCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(modifier.fillMaxWidth().shadow(12.dp, shape, spotColor = P2T.Brand).clip(shape).background(P2T.Hero).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

/** Small rounded label ("pill"). */
@Composable
fun Pill(text: String, bg: Color = P2T.Tint2, fg: Color = P2T.Brand, modifier: Modifier = Modifier) {
    Text(text, modifier.clip(RoundedCornerShape(99.dp)).background(bg).padding(horizontal = 10.dp, vertical = 3.dp), color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
}

/** Uppercase blue section label above a heading. */
@Composable
fun Eyebrow(text: String, color: Color = P2T.Brand) = Text(text.uppercase(), color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)

/** Section heading with an optional action on the right. */
@Composable
fun SectionTitle(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

/** Number + caption tile on the tint background. */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, valueColor: Color = P2T.Ink) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(P2T.Tint).padding(vertical = 10.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = valueColor, maxLines = 1)
        Text(label, fontSize = 11.sp, color = P2T.Muted, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
