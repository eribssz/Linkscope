package dev.linkscope

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun HomeScreen(
    devices: List<DeviceSnapshot>,
    deep: Map<String, DeepInfo>,
    history: List<HistoryEntry>,
    onOpen: (DeviceSnapshot) -> Unit,
    onOpenHistory: (HistoryEntry) -> Unit,
    onClearHistory: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Ink.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulseDot(if (devices.isEmpty()) Ink.Faint else Ink.Mint)
                Spacer(Modifier.width(10.dp))
                Eyebrow("Linkscope · watching ports")
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "Connected",
                    color = Ink.Text,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.8).sp,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    String.format(Locale.US, "%02d", devices.size),
                    color = if (devices.isEmpty()) Ink.Faint else Ink.Mint,
                    fontFamily = Mono,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 7.dp),
                )
            }
        }

        if (devices.isEmpty() && history.isEmpty()) {
            EmptyState(Modifier.weight(1f).fillMaxWidth())
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (devices.isEmpty()) {
                    item(key = "waiting") { WaitingCard(Modifier.animateItem()) }
                } else {
                    items(devices, key = { it.key }) { d ->
                        DeviceCard(d, deep[d.key], Modifier.animateItem()) { onOpen(d) }
                    }
                }
                if (history.isNotEmpty()) {
                    item(key = "history-head") {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 6.dp, top = 18.dp).animateItem(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Eyebrow("History · ${history.size}", modifier = Modifier.weight(1f))
                            ConfirmPill("Clear all", "Tap to clear", Ink.Dim, onClearHistory)
                        }
                    }
                    items(history, key = { "h-${it.id}" }) { e ->
                        HistoryRow(e, Modifier.animateItem()) { onOpenHistory(e) }
                    }
                }
            }
        }
    }
}

/** Compact stand-in for the big empty state when there's history below. */
@Composable
private fun WaitingCard(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, Ink.Line, shape)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseDot(Ink.Faint)
        Spacer(Modifier.width(12.dp))
        Text("Plug something in to test it", color = Ink.Dim, fontSize = 14.sp)
    }
}

@Composable
private fun DeviceCard(device: DeviceSnapshot, deep: DeepInfo?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val d = device.withDeep(deep)
    val link = effectiveLink(d, deep)
    val cap = capabilityOf(d, deep, link)
    val verdict = linkVerdict(d, deep, link, cap)
    val tone = when {
        link == null -> Tone.MUTED
        verdict.tone == Tone.WARN -> Tone.WARN
        link.speed.isSuper -> Tone.GOOD
        else -> Tone.INFO
    }
    val shape = RoundedCornerShape(22.dp)

    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Ink.Surface)
            .border(1.dp, Ink.Line, shape)
            .clickable(onClick = onClick)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KindBadge(d.kind)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    d.product,
                    color = Ink.Text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    d.manufacturer ?: d.kind.label,
                    color = Ink.Dim,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    link?.let { it.speed.value + if (it.atLeast) "+" else "" } ?: "—",
                    color = tone.color,
                    fontFamily = Mono,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Light,
                )
                Text(link?.speed?.unit ?: "link", color = Ink.Faint, fontFamily = Mono, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        SpeedBar(link?.speed?.fraction ?: 0f, tone.color)
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (verdict.tone == Tone.WARN) Tag("bottleneck", Tone.WARN)
            if (d.isStorage) {
                if (d.hasUas) Tag("UASP", Tone.GOOD) else if (d.hasBot) Tag("BOT only", Tone.WARN)
            }
            d.spec?.let { Tag("USB $it", Tone.INFO) }
            Tag(d.ids)
            powerText(d, deep)?.let { Tag(it) }
        }
    }
}

@Composable
fun KindBadge(kind: DeviceKind, large: Boolean = false) {
    val shape = RoundedCornerShape(if (large) 16.dp else 14.dp)
    Box(
        Modifier
            .size(if (large) 52.dp else 44.dp)
            .clip(shape)
            .background(Ink.Raised)
            .border(1.dp, Ink.Line, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(kind.code, color = Ink.Mint, fontFamily = Mono, fontSize = if (large) 15.sp else 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EmptyState(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "pulse")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "p")

    Column(
        modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Canvas(Modifier.size(240.dp)) {
            val c = center
            for (k in 0 until 3) {
                val q = (p + k / 3f) % 1f
                drawCircle(
                    Ink.Mint.copy(alpha = (1f - q) * 0.35f),
                    radius = 40.dp.toPx() + q * 80.dp.toPx(),
                    center = c,
                    style = Stroke(1.dp.toPx()),
                )
            }
            // a USB-C receptacle, drawn rather than borrowed
            val pw = 70.dp.toPx()
            val ph = 24.dp.toPx()
            val tl = Offset(c.x - pw / 2, c.y - ph / 2)
            drawRoundRect(Ink.Raised, topLeft = tl, size = Size(pw, ph), cornerRadius = CornerRadius(ph / 2))
            drawRoundRect(Ink.Dim, topLeft = tl, size = Size(pw, ph), cornerRadius = CornerRadius(ph / 2), style = Stroke(1.5.dp.toPx()))
            val tw = 40.dp.toPx()
            val th = 5.dp.toPx()
            drawRoundRect(Ink.Mint, topLeft = Offset(c.x - tw / 2, c.y - th / 2), size = Size(tw, th), cornerRadius = CornerRadius(th / 2))
        }
        Spacer(Modifier.height(8.dp))
        Text("Plug something in", color = Ink.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "A drive, a cable with a gadget, a hub. Linkscope reads what the link is really doing, not just what the label promises.",
            color = Ink.Dim,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )
    }
}
