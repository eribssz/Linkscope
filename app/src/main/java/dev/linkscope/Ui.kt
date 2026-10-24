package dev.linkscope

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

object Ink {
    val Bg = Color(0xFF0A0B0E)
    val Surface = Color(0xFF121419)
    val Raised = Color(0xFF1A1D24)
    val Line = Color(0xFF242832)
    val Text = Color(0xFFECEEF3)
    val Dim = Color(0xFF8B91A1)
    val Faint = Color(0xFF555B6A)
    val Mint = Color(0xFF5EF0BC)
    val Amber = Color(0xFFFFB547)
    val Coral = Color(0xFFFF6B6B)
    val Sky = Color(0xFF7AA7FF)
}

val Mono = FontFamily.Monospace

val Tone.color: Color
    get() = when (this) {
        Tone.GOOD -> Ink.Mint
        Tone.WARN -> Ink.Amber
        Tone.BAD -> Ink.Coral
        Tone.INFO -> Ink.Sky
        Tone.MUTED -> Ink.Dim
    }

@Composable
fun LinkscopeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Ink.Bg,
            surface = Ink.Surface,
            primary = Ink.Mint,
            onPrimary = Ink.Bg,
            onBackground = Ink.Text,
            onSurface = Ink.Text,
        ),
        content = content,
    )
}

val PanelShape = RoundedCornerShape(22.dp)

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .background(Ink.Surface)
            .border(1.dp, Ink.Line, PanelShape)
            .padding(18.dp),
        content = content,
    )
}

@Composable
fun Eyebrow(text: String, color: Color = Ink.Dim, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 2.sp,
    )
}

@Composable
fun Tag(text: String, tone: Tone = Tone.MUTED) {
    val c = tone.color
    val shape = RoundedCornerShape(50)
    Text(
        text,
        modifier = Modifier
            .clip(shape)
            .background(c.copy(alpha = 0.10f))
            .border(1.dp, c.copy(alpha = 0.28f), shape)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        color = c,
        fontFamily = Mono,
        fontSize = 11.sp,
    )
}

@Composable
fun PulseDot(color: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "dot")
    val a by t.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "a",
    )
    Box(
        modifier
            .size(7.dp)
            .alpha(a)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
fun SpeedBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(fraction) { anim.animateTo(fraction, tween(900, easing = FastOutSlowInEasing)) }
    Canvas(modifier.fillMaxWidth().height(4.dp)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(Ink.Line, cornerRadius = r)
        // markers at 480 Mb/s and 5 Gb/s
        listOf(480.0, 5_000.0).forEach {
            val x = size.width * speedFraction(it)
            drawRect(Ink.Bg, topLeft = Offset(x - 1.dp.toPx(), 0f), size = Size(2.dp.toPx(), size.height))
        }
        if (anim.value > 0f) {
            drawRoundRect(color, size = Size(size.width * anim.value, size.height), cornerRadius = r)
        }
    }
}

/**
 * The centrepiece: a 240° log-scale dial from 1.5 Mb/s to 20 Gb/s.
 * Solid arc = the link right now. Ghost arc + ring = what the device can do.
 */
@Composable
fun SpeedGauge(link: SpeedReading?, capability: LinkSpeed?, tone: Tone, modifier: Modifier = Modifier) {
    val now = remember { Animatable(0f) }
    val cap = remember { Animatable(0f) }
    LaunchedEffect(link?.speed) { now.animateTo(link?.speed?.fraction ?: 0f, tween(1500, easing = FastOutSlowInEasing)) }
    LaunchedEffect(capability) { cap.animateTo(capability?.fraction ?: 0f, tween(1200, delayMillis = 200, easing = FastOutSlowInEasing)) }

    val sweep = rememberInfiniteTransition(label = "scan")
    val shimmer by sweep.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "s")

    val measurer = rememberTextMeasurer()
    val accent = tone.color
    val range = isRange(link, capability)
    val scaleStyle = TextStyle(color = Ink.Faint, fontSize = 10.sp, fontFamily = Mono)
    val valueStyle = TextStyle(color = Ink.Text, fontSize = 56.sp, fontFamily = Mono, fontWeight = FontWeight.Light, letterSpacing = (-2).sp)
    val unitStyle = TextStyle(color = accent, fontSize = 14.sp, fontFamily = Mono)
    val subStyle = TextStyle(color = Ink.Dim, fontSize = 12.sp, letterSpacing = 1.sp)

    Canvas(modifier.fillMaxWidth().aspectRatio(1.25f)) {
        val start = 150f
        val total = 240f
        val stroke = 12.dp.toPx()
        val labelPad = 24.dp.toPx()
        val r = min(size.width * 0.33f, (size.height - labelPad - 16.dp.toPx()) / 1.5f)
        val cx = size.width / 2
        val cy = size.height / 2 + (0.5f * r + labelPad - 12.dp.toPx()) / 2
        val topLeft = Offset(cx - r, cy - r)
        val arcSize = Size(r * 2, r * 2)

        fun point(frac: Float, radius: Float): Offset {
            val rad = Math.toRadians((start + total * frac).toDouble())
            return Offset(cx + radius * cos(rad).toFloat(), cy + radius * sin(rad).toFloat())
        }

        // fine ticks
        for (k in 0..48) {
            val f = k / 48f
            val a = point(f, r + stroke / 2 + 5.dp.toPx())
            val b = point(f, r + stroke / 2 + (if (k % 4 == 0) 9.dp else 7.dp).toPx())
            drawLine(Ink.Line, a, b, strokeWidth = 1.dp.toPx(), cap = StrokeCap.Round)
        }

        // scale labels
        listOf("1.5M" to 1.5, "12M" to 12.0, "480M" to 480.0, "5G" to 5_000.0, "10G" to 10_000.0, "20G" to 20_000.0).forEach { (label, mbps) ->
            val f = speedFraction(mbps)
            val reached = capability != null && capability.mbps >= mbps
            val layout = measurer.measure(label, scaleStyle.copy(color = if (reached) Ink.Dim else Ink.Faint))
            val p = point(f, r + stroke / 2 + 22.dp.toPx())
            drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
            drawLine(Ink.Faint, point(f, r + stroke / 2 + 4.dp.toPx()), point(f, r + stroke / 2 + 11.dp.toPx()), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
        }

        // track
        drawArc(Ink.Line, start, total, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))

        // capability ghost
        if (cap.value > 0f) {
            drawArc(Color.White.copy(alpha = 0.07f), start, total * cap.value, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            // "Could be up to": when the link speed is a lower bound, a faint
            // band carries the arc on to what the device can do.
            if (range && cap.value > now.value && now.value > 0f) {
                drawArc(
                    accent.copy(alpha = 0.28f),
                    start + total * now.value, total * (cap.value - now.value),
                    false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            val cp = point(cap.value, r)
            drawCircle(Ink.Bg, radius = stroke / 2 + 1.dp.toPx(), center = cp)
            drawCircle(Ink.Text.copy(alpha = 0.7f), radius = stroke / 2, center = cp, style = Stroke(1.5.dp.toPx()))
        }

        // live link
        if (now.value > 0f) {
            val sweepDeg = total * now.value
            drawArc(accent.copy(alpha = 0.12f), start, sweepDeg, false, topLeft, arcSize, style = Stroke(stroke * 2.4f, cap = StrokeCap.Round))
            drawArc(accent, start, sweepDeg, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            // travelling glint
            val g = shimmer * now.value
            drawCircle(Color.White.copy(alpha = 0.35f * (1f - shimmer)), radius = stroke * 0.22f, center = point(g, r))
            val tip = point(now.value, r)
            drawCircle(accent, radius = stroke * 0.85f, center = tip)
            drawCircle(Ink.Bg, radius = stroke * 0.32f, center = tip)
        }

        // centre readout
        val speed = link?.speed
        val valueText = when {
            speed == null -> "—"
            link?.atLeast == true -> speed.value + "+"
            else -> speed.value
        }
        val v = measurer.measure(valueText, valueStyle)
        val u = measurer.measure(speed?.unit ?: "", unitStyle)
        val sub = when {
            speed == null -> "speed not visible"
            range && capability != null -> rangeGen(speed, capability)
            else -> speed.gen
        }
        val s = measurer.measure(sub.uppercase(), subStyle)
        val vy = cy - v.size.height * 0.62f
        val gap = 4.dp.toPx()
        val rowW = v.size.width + gap + u.size.width
        drawText(v, topLeft = Offset(cx - rowW / 2, vy))
        drawText(u, topLeft = Offset(cx - rowW / 2 + v.size.width + gap, vy + v.size.height - u.size.height - 9.dp.toPx()))
        drawText(s, topLeft = Offset(cx - s.size.width / 2, vy + v.size.height + 2.dp.toPx()))
    }
}

/** The link figure is a lower bound and the device could go faster. */
fun isRange(link: SpeedReading?, capability: LinkSpeed?): Boolean =
    link != null && link.atLeast && capability != null && capability.ordinal > link.speed.ordinal

/** "USB 3.2 Gen 1 or 2" style subtitle for an uncertain link. */
fun rangeGen(low: LinkSpeed, high: LinkSpeed): String {
    val prefix = "USB 3.2 Gen "
    return if (low.gen.startsWith(prefix) && high.gen.startsWith(prefix)) {
        prefix + low.gen.removePrefix(prefix) + " or " + high.gen.removePrefix(prefix)
    } else {
        "${low.gen} or ${high.gen}"
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun GaugeLegend(tone: Tone, capability: LinkSpeed?, range: Boolean = false) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp, androidx.compose.ui.Alignment.CenterHorizontally),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
    ) {
        LegendItem("Link now") { Box(Modifier.size(8.dp).clip(CircleShape).background(tone.color)) }
        if (range) {
            LegendItem("Could be up to") {
                Box(
                    Modifier
                        .size(width = 14.dp, height = 8.dp)
                        .clip(CircleShape)
                        .background(tone.color.copy(alpha = 0.28f)),
                )
            }
        }
        LegendItem("Device can do" + (capability?.let { " ${it.pretty}" } ?: " ?")) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, Ink.Text.copy(alpha = 0.7f), CircleShape),
            )
        }
    }
}

@Composable
private fun LegendItem(label: String, mark: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        mark()
        Text("  $label", color = Ink.Dim, fontSize = 12.sp)
    }
}
