package dev.linkscope

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

fun whenText(ms: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ms }
    val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))
    val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    val dayDiff = now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR)
    return when {
        sameYear && dayDiff == 0 -> "Today $time"
        sameYear && dayDiff == 1 -> "Yesterday $time"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms)) + " · " + time
    }
}

/** Two-tap confirm: first tap arms it, second tap within 3 s runs it. */
@Composable
fun ConfirmPill(label: String, armedLabel: String, color: Color, onConfirm: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) { if (armed) { delay(3000); armed = false } }
    val c = if (armed) Ink.Coral else color
    val shape = RoundedCornerShape(50)
    Text(
        if (armed) armedLabel else label,
        color = c,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(shape)
            .background(if (armed) Ink.Coral.copy(alpha = 0.12f) else Ink.Surface)
            .border(1.dp, if (armed) Ink.Coral.copy(alpha = 0.4f) else Ink.Line, shape)
            .clickable { if (armed) { armed = false; onConfirm() } else armed = true }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
fun HistoryRow(e: HistoryEntry, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Ink.Surface)
            .border(1.dp, Ink.Line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(e.tone.color))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.product, color = Ink.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                whenText(e.lastSeen) + (if (e.count > 1) "  ·  ×${e.count}" else ""),
                color = Ink.Faint,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                e.link?.let { it.value + if (e.linkAtLeast) "+" else "" } ?: "—",
                color = e.tone.color,
                fontFamily = Mono,
                fontSize = 18.sp,
                fontWeight = FontWeight.Light,
            )
            Text(e.link?.unit ?: "", color = Ink.Faint, fontFamily = Mono, fontSize = 10.sp)
        }
    }
}

@Composable
fun HistoryDetailScreen(e: HistoryEntry, onBack: () -> Unit, onDelete: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1600); copied = false } }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(Ink.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Ink.Surface)
                        .border(1.dp, Ink.Line, CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(16.dp)) {
                        val w = size.width
                        val h = size.height
                        val sw = 2.dp.toPx()
                        drawLine(Ink.Text, Offset(w * 0.62f, h * 0.18f), Offset(w * 0.28f, h * 0.5f), sw, StrokeCap.Round)
                        drawLine(Ink.Text, Offset(w * 0.28f, h * 0.5f), Offset(w * 0.62f, h * 0.82f), sw, StrokeCap.Round)
                    }
                }
                Spacer(Modifier.weight(1f))
                ConfirmPill("Delete", "Tap to delete", Ink.Dim, onDelete)
                Spacer(Modifier.width(8.dp))
                val shape = RoundedCornerShape(50)
                Text(
                    if (copied) "Copied ✓" else "Copy report",
                    color = if (copied) Ink.Mint else Ink.Dim,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(shape)
                        .background(Ink.Surface)
                        .border(1.dp, Ink.Line, shape)
                        .clickable {
                            clipboard.setText(AnnotatedString(e.report))
                            copied = true
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }

        item {
            Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KindBadge(e.kind, large = true)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Eyebrow("History · ${e.kind.label}")
                        Spacer(Modifier.height(2.dp))
                        Text(e.manufacturer ?: "Unknown maker", color = Ink.Dim, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(e.product, color = Ink.Text, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tag(e.ids)
                    e.spec?.let { Tag("spec USB $it", Tone.INFO) }
                    if (e.count > 1) Tag("seen ×${e.count}")
                }
            }
        }

        item {
            Panel {
                Eyebrow("Link · ${whenText(e.lastSeen)}")
                SpeedGauge(e.linkReading, e.capability, e.tone)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    GaugeLegend(e.tone, e.capability)
                }
            }
        }

        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(e.tone.color))
                    Spacer(Modifier.width(10.dp))
                    Eyebrow("Cable & port")
                }
                Spacer(Modifier.height(10.dp))
                Text(e.headline, color = e.tone.color, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(e.body, color = Ink.Dim, fontSize = 14.sp, lineHeight = 20.sp)
                if (e.storageHeadline != null) {
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Eyebrow("Protocol  ")
                        Text(e.storageHeadline, color = (e.storageTone ?: Tone.INFO).color, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        item {
            Panel {
                Eyebrow("Report")
                Spacer(Modifier.height(10.dp))
                Text(e.report, color = Ink.Dim, fontFamily = Mono, fontSize = 11.sp, lineHeight = 16.sp)
            }
        }

        item {
            Text(
                if (e.count > 1) "First seen ${whenText(e.firstSeen)}" else "",
                color = Ink.Faint,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun HistoryScreen(
    history: List<HistoryEntry>,
    onBack: () -> Unit,
    onOpen: (HistoryEntry) -> Unit,
    onClear: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Ink.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onBack)
            Spacer(Modifier.weight(1f))
            if (history.isNotEmpty()) ConfirmPill("Clear all", "Tap to clear", Ink.Dim, onClear)
        }
        Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 8.dp)) {
            Eyebrow("Linkscope · past results")
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("History", color = Ink.Text, fontSize = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    String.format(java.util.Locale.US, "%02d", history.size),
                    color = if (history.isEmpty()) Ink.Faint else Ink.Mint,
                    fontFamily = Mono,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 7.dp),
                )
            }
        }
        if (history.isEmpty()) {
            Text(
                "Nothing yet. Every device you plug in is recorded here.",
                color = Ink.Dim,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(32.dp),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(history.size, key = { history[it].id }) { i ->
                    HistoryRow(history[i], Modifier.animateItem()) { onOpen(history[i]) }
                }
            }
        }
    }
}

@Composable
fun BackButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Ink.Surface)
            .border(1.dp, Ink.Line, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(16.dp)) {
            val w = size.width
            val h = size.height
            val sw = 2.dp.toPx()
            drawLine(Ink.Text, Offset(w * 0.62f, h * 0.18f), Offset(w * 0.28f, h * 0.5f), sw, StrokeCap.Round)
            drawLine(Ink.Text, Offset(w * 0.28f, h * 0.5f), Offset(w * 0.62f, h * 0.82f), sw, StrokeCap.Round)
        }
    }
}
