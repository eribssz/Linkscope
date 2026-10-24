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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale

/** Everything the detail screen shows, from a live device or a saved entry. */
data class DetailModel(
    val kind: DeviceKind,
    val product: String,
    val manufacturer: String?,
    val tags: List<Pair<String, Tone>>,
    val statusTag: Pair<String, Tone>?,
    val gaugeLabel: String,
    val link: SpeedReading?,
    val capability: LinkSpeed?,
    val verdict: Verdict,
    val storage: Verdict?,
    val interfaces: List<InterfaceInfo>,
    val power: String?,
    val spec: String?,
    val serial: String?,
    val footer: String,
    val report: String,
)

@Composable
fun DetailScreen(
    device: DeviceSnapshot,
    deep: DeepInfo?,
    scanning: Boolean,
    connected: Boolean,
    onBack: () -> Unit,
    onDeepScan: () -> Unit,
) {
    val d = device.withDeep(deep)
    val link = effectiveLink(d, deep)
    val cap = capabilityOf(d, deep, link)
    val model = DetailModel(
        kind = d.kind,
        product = d.product,
        manufacturer = d.manufacturer,
        tags = listOfNotNull(d.ids to Tone.MUTED, d.busLabel to Tone.MUTED, d.spec?.let { "spec USB $it" to Tone.INFO }),
        statusTag = if (connected) null else "unplugged" to Tone.BAD,
        gaugeLabel = "Link",
        link = link,
        capability = cap,
        verdict = linkVerdict(d, deep, link, cap),
        storage = storageVerdict(d),
        interfaces = d.interfaces,
        power = powerText(d, deep),
        spec = d.spec,
        serial = deep?.serial,
        footer = d.key,
        report = buildReport(device, deep),
    )
    DetailBody(
        model = model,
        onBack = onBack,
        extra = if (!d.hasPermission || scanning || deep?.error != null) {
            { DeepScanCard(d.hasPermission, scanning, deep?.error, onDeepScan) }
        } else {
            null
        },
    )
}

@Composable
fun DetailBody(
    model: DetailModel,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
    extra: (@Composable () -> Unit)? = null,
) {
    val m = model
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
        // top bar
        item(key = "bar") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundButton(onBack) {
                    Canvas(Modifier.size(16.dp)) {
                        val w = size.width
                        val h = size.height
                        val sw = 2.dp.toPx()
                        drawLine(Ink.Text, Offset(w * 0.62f, h * 0.18f), Offset(w * 0.28f, h * 0.5f), sw, StrokeCap.Round)
                        drawLine(Ink.Text, Offset(w * 0.28f, h * 0.5f), Offset(w * 0.62f, h * 0.82f), sw, StrokeCap.Round)
                    }
                }
                Spacer(Modifier.weight(1f))
                actions()
                PillButton(if (copied) "Copied ✓" else "Copy report", if (copied) Ink.Mint else Ink.Dim) {
                    clipboard.setText(AnnotatedString(m.report))
                    copied = true
                }
            }
        }

        // hero
        item(key = "hero") {
            Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KindBadge(m.kind, large = true)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Eyebrow(m.kind.label)
                            m.statusTag?.let { (text, tone) ->
                                Spacer(Modifier.width(8.dp))
                                Tag(text, tone)
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(m.manufacturer ?: "Unknown maker", color = Ink.Dim, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    m.product,
                    color = Ink.Text,
                    fontSize = 30.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.6).sp,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    m.tags.forEach { (text, tone) -> Tag(text, tone) }
                }
            }
        }

        // gauge
        item(key = "gauge") {
            Panel {
                Eyebrow(m.gaugeLabel)
                SpeedGauge(m.link, m.capability, m.verdict.tone)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    GaugeLegend(m.verdict.tone, m.capability, isRange(m.link, m.capability))
                }
            }
        }

        // verdicts
        item(key = "link-verdict") {
            VerdictCard("Cable & port", m.verdict, m.link?.source?.text)
        }
        if (m.storage != null) {
            item(key = "storage") {
                VerdictCard("Protocol", m.storage, null) {
                    val ladder = m.interfaces.filter { it.cls == 0x08 }
                    if (ladder.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        ProtocolLadder(ladder)
                    }
                }
            }
        }

        if (extra != null) item(key = "extra") { extra() }

        // facts
        item(key = "facts") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Fact("Power ask", m.power ?: "—", Modifier.weight(1f))
                Fact("Spec", m.spec?.let { "USB $it" } ?: "—", Modifier.weight(1f))
            }
        }
        m.serial?.let { serial ->
            item(key = "serial") { Fact("Serial", serial, Modifier.fillMaxWidth()) }
        }

        // descriptor tree
        if (m.interfaces.isNotEmpty()) {
            item(key = "iface-head") {
                Eyebrow("Interfaces · ${m.interfaces.size}", modifier = Modifier.padding(start = 6.dp, top = 14.dp, bottom = 2.dp))
            }
            items(m.interfaces) { InterfaceCard(it) }
        }

        item(key = "foot") {
            Text(
                m.footer,
                color = Ink.Faint,
                fontFamily = Mono,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun RoundButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Ink.Surface)
            .border(1.dp, Ink.Line, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun PillButton(text: String, color: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        text,
        color = color,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(shape)
            .background(Ink.Surface)
            .border(1.dp, Ink.Line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun VerdictCard(
    title: String,
    v: Verdict,
    footnote: String?,
    extra: @Composable () -> Unit = {},
) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(v.tone.color))
            Spacer(Modifier.width(10.dp))
            Eyebrow(title)
        }
        Spacer(Modifier.height(10.dp))
        Text(v.headline, color = v.tone.color, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
        Spacer(Modifier.height(6.dp))
        Text(v.body, color = Ink.Dim, fontSize = 14.sp, lineHeight = 20.sp)
        extra()
        footnote?.let {
            Spacer(Modifier.height(12.dp))
            Text("↳ $it", color = Ink.Faint, fontSize = 12.sp, fontFamily = Mono)
        }
    }
}

/** Each storage alt-setting as a rung: BOT (legacy) and UAS (fast lane). */
@Composable
private fun ProtocolLadder(storage: List<InterfaceInfo>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        storage.forEach { i ->
            val isUas = i.proto == 0x62
            val isBot = i.proto == 0x50
            val c = when {
                isUas -> Ink.Mint
                isBot -> Ink.Dim
                else -> Ink.Sky
            }
            val shape = RoundedCornerShape(12.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Ink.Raised)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "alt ${i.alt}",
                    color = Ink.Faint,
                    fontFamily = Mono,
                    fontSize = 12.sp,
                    modifier = Modifier.width(48.dp),
                )
                Text(
                    when {
                        isUas -> "UAS — parallel queue"
                        isBot -> "BOT — one at a time"
                        else -> protocolDetail(i) ?: "storage"
                    },
                    color = c,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(String.format(Locale.US, "0x%02X", i.proto), color = Ink.Faint, fontFamily = Mono, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DeepScanCard(hasPermission: Boolean, scanning: Boolean, error: String?, onRequest: () -> Unit) {
    Panel {
        Eyebrow("Deep scan", Ink.Sky)
        Spacer(Modifier.height(10.dp))
        when {
            scanning -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), color = Ink.Sky, strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Reading descriptors…", color = Ink.Text, fontSize = 15.sp)
            }
            error != null -> Text(error, color = Ink.Coral, fontSize = 14.sp)
            !hasPermission -> {
                Text("See the device's true top speed", color = Ink.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Android asks once. Linkscope then reads the capability descriptors to tell a genuine USB 2 device " +
                        "from a USB 3 device held back by its cable. Read-only — nothing is written to the device.",
                    color = Ink.Dim,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(16.dp))
                val shape = RoundedCornerShape(50)
                Text(
                    "Allow access",
                    color = Ink.Bg,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(shape)
                        .background(Ink.Sky)
                        .clickable(onClick = onRequest)
                        .padding(horizontal = 22.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier = Modifier) {
    Panel(modifier) {
        Eyebrow(label)
        Spacer(Modifier.height(8.dp))
        // Shrink long values so they stay on one line instead of breaking mid-word.
        Text(
            value,
            color = Ink.Text,
            fontFamily = Mono,
            fontSize = when {
                value.length <= 9 -> 20.sp
                value.length <= 12 -> 16.sp
                else -> 14.sp
            },
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun InterfaceCard(i: InterfaceInfo) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("#${i.id}", color = Ink.Mint, fontFamily = Mono, fontSize = 14.sp)
            if (i.alt > 0) Text("  alt ${i.alt}", color = Ink.Faint, fontFamily = Mono, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text(
                String.format(Locale.US, "%02X · %02X · %02X", i.cls, i.sub, i.proto),
                color = Ink.Faint,
                fontFamily = Mono,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(className(i.cls), color = Ink.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        val detail = listOfNotNull(protocolDetail(i), i.name).joinToString(" · ")
        if (detail.isNotEmpty()) Text(detail, color = Ink.Dim, fontSize = 13.sp)
        if (i.endpoints.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            i.endpoints.forEach { e ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        String.format(Locale.US, "0x%02X", e.address),
                        color = Ink.Dim, fontFamily = Mono, fontSize = 12.sp,
                        modifier = Modifier.width(52.dp),
                    )
                    Text(
                        if (e.isIn) "↓ IN" else "↑ OUT",
                        color = if (e.isIn) Ink.Sky else Ink.Amber,
                        fontFamily = Mono, fontSize = 12.sp,
                        modifier = Modifier.width(56.dp),
                    )
                    Text(e.typeName, color = Ink.Dim, fontFamily = Mono, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("${e.maxPacket} B", color = Ink.Text, fontFamily = Mono, fontSize = 12.sp)
                }
            }
        }
    }
}
