package app.paper2test.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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

/** Paper2Test design tokens (same as the website's style.css, from the Stitch redesign). */
object P2T {
    val Brand = Color(0xFF004AC6)
    val Brand2 = Color(0xFF2563EB)
    val Indigo = Color(0xFF4B41E1)
    val Canvas = Color(0xFFF8FAFC)
    val Tint = Color(0xFFEFF4FF)
    val Tint2 = Color(0xFFE5EEFF)
    val Tint3 = Color(0xFFDCE9FF)
    val Ink = Color(0xFF0F172A)
    val Ink2 = Color(0xFF434655)
    val Muted = Color(0xFF64748B)
    val Line = Color(0xFFE2E8F0)
    val Ok = Color(0xFF10B981)
    val OkInk = Color(0xFF047857)
    val Bad = Color(0xFFBA1A1A)
    val Warn = Color(0xFFF59E0B)
    val Hero = Brush.linearGradient(listOf(Color(0xFF0B3FB0), Brand2, Indigo))
}

val Jakarta = FontFamily(
    Font(R.font.jakarta_600, FontWeight.SemiBold),
    Font(R.font.jakarta_700, FontWeight.Bold),
    Font(R.font.jakarta_800, FontWeight.ExtraBold),
)

private val Colors = lightColorScheme(
    primary = P2T.Brand, onPrimary = Color.White, primaryContainer = P2T.Tint2, onPrimaryContainer = P2T.Brand,
    secondary = P2T.Indigo, onSecondary = Color.White, secondaryContainer = Color(0xFFE2DFFF), onSecondaryContainer = Color(0xFF3323CC),
    tertiary = P2T.OkInk, tertiaryContainer = Color(0xFFECFDF5), onTertiaryContainer = P2T.OkInk,
    background = P2T.Canvas, onBackground = P2T.Ink, surface = P2T.Canvas, onSurface = P2T.Ink, onSurfaceVariant = P2T.Ink2,
    surfaceVariant = P2T.Tint, surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White, surfaceContainer = P2T.Tint,
    surfaceContainerHigh = P2T.Tint2, surfaceContainerHighest = P2T.Tint3, outline = Color(0xFF737686), outlineVariant = P2T.Line,
    error = P2T.Bad, errorContainer = Color(0xFFFFDAD6),
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
fun P2TTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = Colors, typography = Type, shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(18.dp)), content = content)

/** White card with a hairline border and a soft shadow (the website's .card). */
@Composable
fun P2TCard(modifier: Modifier = Modifier, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.fillMaxWidth().shadow(6.dp, shape, ambientColor = Color(0x140F172A), spotColor = Color(0x140F172A)).clip(shape).background(Color.White).border(1.dp, P2T.Line, shape).padding(padding),
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
