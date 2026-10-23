package dev.linkscope

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import java.io.File
import java.util.Locale
import kotlin.math.log10

// ---------------------------------------------------------------------------
// Speeds
// ---------------------------------------------------------------------------

enum class LinkSpeed(
    val mbps: Double,
    val value: String,
    val unit: String,
    val gen: String,
    val nick: String,
) {
    LOW(1.5, "1.5", "Mb/s", "USB 1.0", "Low Speed"),
    FULL(12.0, "12", "Mb/s", "USB 1.1", "Full Speed"),
    HIGH(480.0, "480", "Mb/s", "USB 2.0", "High Speed"),
    SUPER(5_000.0, "5", "Gb/s", "USB 3.2 Gen 1", "SuperSpeed"),
    SUPER_PLUS(10_000.0, "10", "Gb/s", "USB 3.2 Gen 2", "SuperSpeed+"),
    SUPER_PLUS_2X2(20_000.0, "20", "Gb/s", "USB 3.2 Gen 2×2", "SuperSpeed+ 2×2");

    val isSuper: Boolean get() = ordinal >= SUPER.ordinal
    val fraction: Float get() = speedFraction(mbps)
    val pretty: String get() = "$value $unit"
}

/** Position of a speed on a log scale from 1.5 Mb/s to 20 Gb/s. */
fun speedFraction(mbps: Double): Float {
    val lo = log10(1.5)
    val hi = log10(20_000.0)
    return ((log10(mbps) - lo) / (hi - lo)).toFloat().coerceIn(0f, 1f)
}

fun maxSpeed(a: LinkSpeed?, b: LinkSpeed?): LinkSpeed? = when {
    a == null -> b
    b == null -> a
    else -> if (a.ordinal >= b.ordinal) a else b
}

enum class SpeedSource(val text: String) {
    KERNEL("Reported by the kernel"),
    COMPANION("SuperSpeed descriptors are present"),
    ENDPOINTS("Inferred from bulk packet size"),
    SPEC("Implied by the device's USB 1.x spec"),
}

/** The speed the link is running at right now. [atLeast] = could be faster than shown. */
data class SpeedReading(val speed: LinkSpeed, val source: SpeedSource, val atLeast: Boolean = false)

// ---------------------------------------------------------------------------
// Snapshot of a device (no permission needed)
// ---------------------------------------------------------------------------

data class EndpointInfo(
    val address: Int,
    val isIn: Boolean,
    val type: Int,
    val maxPacket: Int,
    val interval: Int,
) {
    val typeName: String
        get() = when (type) {
            UsbConstants.USB_ENDPOINT_XFER_BULK -> "BULK"
            UsbConstants.USB_ENDPOINT_XFER_INT -> "INT"
            UsbConstants.USB_ENDPOINT_XFER_ISOC -> "ISO"
            else -> "CTRL"
        }
}

data class InterfaceInfo(
    val id: Int,
    val alt: Int,
    val cls: Int,
    val sub: Int,
    val proto: Int,
    val name: String?,
    val endpoints: List<EndpointInfo>,
)

data class DeviceSnapshot(
    val key: String,
    val device: UsbDevice,
    val product: String,
    val manufacturer: String?,
    val vid: Int,
    val pid: Int,
    val spec: String?,
    val deviceClass: Int,
    val interfaces: List<InterfaceInfo>,
    val maxPowerMa: Int?,
    val hasPermission: Boolean,
    val link: SpeedReading?,
) {
    val specNumber: Double? get() = spec?.toDoubleOrNull()
    val isStorage: Boolean get() = interfaces.any { it.cls == 0x08 }
    val hasUas: Boolean get() = interfaces.any { it.cls == 0x08 && it.proto == 0x62 }
    val hasBot: Boolean get() = interfaces.any { it.cls == 0x08 && it.proto == 0x50 }
    val kind: DeviceKind get() = DeviceKind.of(this)
    val ids: String get() = String.format(Locale.US, "%04X:%04X", vid, pid)
    val busLabel: String
        get() {
            val p = key.trimEnd('/').split('/')
            return if (p.size >= 2) "Bus ${p[p.size - 2]} · Dev ${p.last()}" else key
        }
}

enum class DeviceKind(val code: String, val label: String) {
    STORAGE("SD", "Storage"),
    VIDEO("VI", "Camera"),
    AUDIO("AU", "Audio"),
    HID("IN", "Input"),
    HUB("HB", "Hub"),
    COMMS("CO", "Network / Serial"),
    PRINTER("PR", "Printer"),
    IMAGING("IM", "Imaging"),
    SMARTCARD("SC", "Smart card"),
    WIRELESS("WL", "Wireless"),
    BILLBOARD("BB", "Billboard"),
    VENDOR("VS", "Vendor-specific"),
    OTHER("··", "USB device");

    companion object {
        private fun ofClass(c: Int): DeviceKind? = when (c) {
            0x08 -> STORAGE
            0x0E, 0x10 -> VIDEO
            0x01 -> AUDIO
            0x03 -> HID
            0x09 -> HUB
            0x02, 0x0A -> COMMS
            0x07 -> PRINTER
            0x06 -> IMAGING
            0x0B -> SMARTCARD
            0xE0 -> WIRELESS
            0x11 -> BILLBOARD
            0xFF -> VENDOR
            else -> null
        }

        fun of(s: DeviceSnapshot): DeviceKind {
            val found = (s.interfaces.map { it.cls } + s.deviceClass).mapNotNull { ofClass(it) }.toSet()
            return entries.firstOrNull { it in found } ?: OTHER
        }
    }
}

fun className(c: Int): String = when (c) {
    0x01 -> "Audio"
    0x02 -> "Communications"
    0x03 -> "HID"
    0x05 -> "Physical"
    0x06 -> "Imaging"
    0x07 -> "Printer"
    0x08 -> "Mass Storage"
    0x09 -> "Hub"
    0x0A -> "CDC Data"
    0x0B -> "Smart Card"
    0x0D -> "Content Security"
    0x0E -> "Video"
    0x0F -> "Healthcare"
    0x10 -> "Audio/Video"
    0x11 -> "Billboard"
    0x12 -> "Type-C Bridge"
    0xDC -> "Diagnostic"
    0xE0 -> "Wireless"
    0xEF -> "Miscellaneous"
    0xFE -> "App-specific"
    0xFF -> "Vendor-specific"
    else -> String.format(Locale.US, "Class 0x%02X", c)
}

fun protocolDetail(i: InterfaceInfo): String? = when (i.cls) {
    0x08 -> listOfNotNull(
        when (i.sub) {
            0x06 -> "SCSI"; 0x02 -> "ATAPI"; 0x04 -> "UFI"; 0x05 -> "SFF-8070i"; else -> null
        },
        when (i.proto) {
            0x50 -> "BOT"; 0x62 -> "UAS"; 0x00, 0x01 -> "CBI"; else -> null
        },
    ).joinToString(" · ").ifEmpty { null }
    0x03 -> when (i.proto) {
        1 -> "Keyboard"; 2 -> "Mouse"; else -> if (i.sub == 1) "Boot device" else null
    }
    0x01 -> when (i.sub) {
        1 -> "Control"; 2 -> "Streaming"; 3 -> "MIDI"; else -> null
    }
    0x0E -> when (i.sub) {
        1 -> "Control"; 2 -> "Streaming"; else -> null
    }
    else -> null
}

/**
 * Android's UsbDevice.version isn't reliable: on some phones it returns the
 * device's firmware revision instead of its USB spec. Keep it only when it
 * looks like a real spec; the deep scan reads the true value (bcdUSB).
 */
private fun cleanSpec(v: String?): String? {
    val d = v?.trim()?.toDoubleOrNull() ?: return null
    if (d < 1.0 || d > 4.0) return null
    return formatSpec(d)
}

private fun formatSpec(d: Double): String {
    val t = String.format(Locale.US, "%.2f", d).trimEnd('0')
    return if (t.endsWith('.')) t + "0" else t
}

/** bcdUSB 0x0320 → "3.2", 0x0210 → "2.1", 0x0200 → "2.0". */
fun bcdToSpec(bcd: Int): String {
    val major = (bcd shr 8) and 0xFF
    val minor = (bcd shr 4) and 0xF
    val sub = bcd and 0xF
    return if (sub != 0) "$major.$minor$sub" else "$major.$minor"
}

/** Snapshot with the values the deep scan read straight from the device. */
fun DeviceSnapshot.withDeep(d: DeepInfo?): DeviceSnapshot {
    if (d == null || d.error != null) return this
    return copy(spec = d.spec ?: spec, maxPowerMa = d.maxPowerMa ?: maxPowerMa)
}

fun powerText(s: DeviceSnapshot, d: DeepInfo?): String? {
    val ma = s.maxPowerMa ?: return null
    return if (ma == 0 && d?.selfPowered == true) "Self-powered" else "$ma mA"
}

object UsbScanner {

    fun scan(manager: UsbManager): List<DeviceSnapshot> =
        manager.deviceList.values
            .sortedBy { it.deviceName }
            .mapNotNull { runCatching { snapshot(manager, it) }.getOrNull() }

    private fun snapshot(manager: UsbManager, d: UsbDevice): DeviceSnapshot {
        val ifaces = (0 until d.interfaceCount).map { i ->
            val itf = d.getInterface(i)
            InterfaceInfo(
                id = itf.id,
                alt = itf.alternateSetting,
                cls = itf.interfaceClass,
                sub = itf.interfaceSubclass,
                proto = itf.interfaceProtocol,
                name = runCatching { itf.name }.getOrNull(),
                endpoints = (0 until itf.endpointCount).map { e ->
                    val ep = itf.getEndpoint(e)
                    EndpointInfo(
                        address = ep.address,
                        isIn = ep.direction == UsbConstants.USB_DIR_IN,
                        type = ep.type,
                        maxPacket = ep.maxPacketSize,
                        interval = ep.interval,
                    )
                },
            )
        }
        val spec = cleanSpec(runCatching { d.version }.getOrNull())
        val power = runCatching {
            if (d.configurationCount > 0) d.getConfiguration(0).maxPower else null
        }.getOrNull()
        val manufacturer = runCatching { d.manufacturerName }.getOrNull()?.trim()?.ifEmpty { null }
        val product = runCatching { d.productName }.getOrNull()?.trim()?.ifEmpty { null }
        val kernel = kernelSpeed(d)
        val link = kernel?.let { SpeedReading(it, SpeedSource.KERNEL) }
            ?: inferFromEndpoints(ifaces, spec?.toDoubleOrNull())

        val snap = DeviceSnapshot(
            key = d.deviceName,
            device = d,
            product = product ?: "",
            manufacturer = manufacturer,
            vid = d.vendorId,
            pid = d.productId,
            spec = spec,
            deviceClass = d.deviceClass,
            interfaces = ifaces,
            maxPowerMa = power,
            hasPermission = manager.hasPermission(d),
            link = link,
        )
        return if (product == null) snap.copy(product = snap.kind.label) else snap
    }

    /**
     * Bulk endpoints have a fixed max packet size per speed:
     * 64 B at Full Speed, 512 B at High Speed, 1024 B at SuperSpeed.
     * The kernel reports the descriptors for the speed the link actually
     * negotiated, so a USB 3 drive on a USB 2 cable shows 512 here.
     */
    private fun inferFromEndpoints(ifaces: List<InterfaceInfo>, spec: Double?): SpeedReading? {
        val bulk = ifaces.flatMap { it.endpoints }.filter { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK }
        val maxBulk = bulk.maxOfOrNull { it.maxPacket }
        return when {
            maxBulk != null && maxBulk >= 1024 -> SpeedReading(LinkSpeed.SUPER, SpeedSource.ENDPOINTS, atLeast = true)
            maxBulk != null && maxBulk >= 512 -> SpeedReading(LinkSpeed.HIGH, SpeedSource.ENDPOINTS)
            maxBulk != null -> SpeedReading(LinkSpeed.FULL, SpeedSource.ENDPOINTS)
            spec != null && spec < 2.0 -> SpeedReading(LinkSpeed.FULL, SpeedSource.SPEC)
            else -> null
        }
    }

    /** Many phones block this, but when sysfs is readable it is the ground truth. */
    private fun kernelSpeed(d: UsbDevice): LinkSpeed? = runCatching {
        val parts = d.deviceName.trimEnd('/').split('/')
        val bus = parts[parts.size - 2].toInt()
        val dev = parts.last().toInt()
        val dirs = File("/sys/bus/usb/devices").listFiles() ?: return@runCatching null
        val raw = dirs.firstNotNullOfOrNull { dir ->
            val b = File(dir, "busnum").readTextOrNull()?.toIntOrNull()
            val n = File(dir, "devnum").readTextOrNull()?.toIntOrNull()
            if (b == bus && n == dev) File(dir, "speed").readTextOrNull() else null
        }
        when (raw) {
            "1.5" -> LinkSpeed.LOW
            "12" -> LinkSpeed.FULL
            "480" -> LinkSpeed.HIGH
            "5000" -> LinkSpeed.SUPER
            "10000" -> LinkSpeed.SUPER_PLUS
            "20000" -> LinkSpeed.SUPER_PLUS_2X2
            else -> null
        }
    }.getOrNull()

    private fun File.readTextOrNull(): String? =
        runCatching { if (canRead()) readText().trim() else null }.getOrNull()
}

// ---------------------------------------------------------------------------
// Deep scan (needs permission): raw descriptors + BOS capability descriptor
// ---------------------------------------------------------------------------

data class DeepInfo(
    /** Top speed the device says it supports (from BOS). Null if no BOS. */
    val capability: LinkSpeed?,
    val hasBos: Boolean,
    /** SuperSpeed endpoint companion descriptors seen → link is SuperSpeed now. */
    val hasCompanion: Boolean,
    val maxPowerMa: Int?,
    val serial: String?,
    val error: String? = null,
    /** bcdUSB from the device descriptor, e.g. "3.2". */
    val spec: String? = null,
    val selfPowered: Boolean? = null,
)

private val Byte.u: Int get() = toInt() and 0xFF
private fun u16(b: ByteArray, i: Int): Int = b[i].u or (b[i + 1].u shl 8)
private fun u32(b: ByteArray, i: Int): Long =
    (b[i].u.toLong()) or (b[i + 1].u.toLong() shl 8) or (b[i + 2].u.toLong() shl 16) or (b[i + 3].u.toLong() shl 24)

object DeepScanner {

    fun scan(manager: UsbManager, device: UsbDevice): DeepInfo {
        val conn = runCatching { manager.openDevice(device) }.getOrNull()
            ?: return DeepInfo(null, false, false, null, null, "Android wouldn't open the device.")
        try {
            val raw = runCatching { conn.rawDescriptors }.getOrNull() ?: ByteArray(0)
            var companion = false
            var bMaxPower: Int? = null
            var bmAttributes: Int? = null
            var bcdUsb: Int? = null
            var i = 0
            while (i + 1 < raw.size) {
                val len = raw[i].u
                if (len < 2) break
                when (raw[i + 1].u) {
                    0x30, 0x31 -> companion = true
                    0x01 -> if (bcdUsb == null && i + 3 < raw.size) bcdUsb = u16(raw, i + 2)
                    0x02 -> if (bMaxPower == null && i + 8 < raw.size) {
                        bmAttributes = raw[i + 7].u
                        bMaxPower = raw[i + 8].u
                    }
                }
                i += len
            }
            val bos = runCatching { readBos(conn) }.getOrNull()
            val serial = runCatching { conn.serial }.getOrNull()?.trim()?.ifEmpty { null }
            // bMaxPower is in 8 mA units at SuperSpeed, 2 mA units otherwise.
            val power = bMaxPower?.let { if (companion) it * 8 else it * 2 }
            return DeepInfo(
                capability = bos?.second,
                hasBos = bos != null,
                hasCompanion = companion,
                maxPowerMa = power,
                serial = serial,
                spec = bcdUsb?.takeIf { it in 0x0100..0x0400 }?.let { bcdToSpec(it) },
                selfPowered = bmAttributes?.let { (it and 0x40) != 0 },
            )
        } finally {
            conn.close()
        }
    }

    /** Returns (present, max speed the device advertises). */
    private fun readBos(conn: UsbDeviceConnection): Pair<Boolean, LinkSpeed?>? {
        val reqType = 0x80 // device-to-host, standard, device
        val getDescriptor = 0x06
        val value = 0x0F shl 8 // BOS
        val head = ByteArray(5)
        val n = conn.controlTransfer(reqType, getDescriptor, value, 0, head, head.size, 500)
        if (n < 5 || head[1].u != 0x0F) return null
        val total = u16(head, 2).coerceIn(5, 1024)
        val buf = ByteArray(total)
        val m = conn.controlTransfer(reqType, getDescriptor, value, 0, buf, total, 500)
        if (m < 5) return null

        var best: LinkSpeed? = null
        var i = buf[0].u.coerceAtLeast(5)
        while (i + 2 < m) {
            val len = buf[i].u
            if (len < 3 || i + len > m) break
            if (buf[i + 1].u == 0x10) { // DEVICE CAPABILITY
                when (buf[i + 2].u) {
                    0x03 -> if (len >= 6) { // SuperSpeed USB
                        val speeds = u16(buf, i + 4)
                        if ((speeds and 0x08) != 0) best = maxSpeed(best, LinkSpeed.SUPER)
                        else if ((speeds and 0x04) != 0) best = maxSpeed(best, LinkSpeed.HIGH)
                    }
                    0x0A -> if (len >= 12) { // SuperSpeedPlus USB
                        val attrs = u32(buf, i + 4)
                        val count = (attrs and 0x1F).toInt() + 1
                        var laneGbps = 0.0
                        for (k in 0 until count) {
                            val off = i + 12 + 4 * k
                            if (off + 4 > i + len) break
                            val a = u32(buf, off)
                            val exponent = ((a shr 4) and 0x3).toInt()
                            val mantissa = ((a shr 16) and 0xFFFF).toDouble()
                            val gbps = mantissa * when (exponent) {
                                0 -> 1e-9; 1 -> 1e-6; 2 -> 1e-3; else -> 1.0
                            }
                            if (gbps > laneGbps) laneGbps = gbps
                        }
                        val ssp = when {
                            laneGbps >= 19.0 -> LinkSpeed.SUPER_PLUS_2X2
                            laneGbps >= 9.0 -> LinkSpeed.SUPER_PLUS
                            else -> LinkSpeed.SUPER
                        }
                        best = maxSpeed(best, ssp)
                    }
                }
            }
            i += len
        }
        return true to best
    }
}

// ---------------------------------------------------------------------------
// Interpretation
// ---------------------------------------------------------------------------

fun effectiveLink(s: DeviceSnapshot, d: DeepInfo?): SpeedReading? {
    val link = s.link
    if (link?.source == SpeedSource.KERNEL) return link
    if (d?.hasCompanion == true && (link == null || !link.speed.isSuper)) {
        return SpeedReading(LinkSpeed.SUPER, SpeedSource.COMPANION, atLeast = true)
    }
    return link
}

/** What the device could do on a perfect cable and port. Null = unknown. */
fun capabilityOf(s: DeviceSnapshot, d: DeepInfo?, link: SpeedReading?): LinkSpeed? {
    val spec = s.specNumber
    val fromLink = link?.speed
    d?.capability?.let { return maxSpeed(it, fromLink) }
    if (fromLink?.isSuper == true) return fromLink
    if (spec != null && spec >= 3.0) return LinkSpeed.SUPER
    // USB 3 devices report 2.10 when stuck on a USB 2 link, so 2.1 alone
    // proves nothing. Once we've read descriptors and found no SuperSpeed
    // capability, the device really is USB 2.
    if (d != null && d.error == null) return if (spec != null && spec < 2.0) LinkSpeed.FULL else maxSpeed(LinkSpeed.HIGH, fromLink)
    if (spec != null && spec < 2.1) return if (spec < 2.0) LinkSpeed.FULL else LinkSpeed.HIGH
    return null
}

enum class Tone { GOOD, WARN, BAD, INFO, MUTED }

data class Verdict(val tone: Tone, val headline: String, val body: String)

fun linkVerdict(s: DeviceSnapshot, d: DeepInfo?, link: SpeedReading?, cap: LinkSpeed?): Verdict {
    if (link == null) {
        return Verdict(
            Tone.MUTED, "Speed hidden",
            "This device has no bulk endpoints and Android doesn't expose the negotiated speed. " +
                "Plug in a USB 3 drive to test a cable or adapter.",
        )
    }
    val now = link.speed
    if (cap != null && cap.isSuper && !now.isSuper) {
        return Verdict(
            Tone.WARN, "Held back to ${now.gen}",
            "The device can run at ${cap.pretty}, but the link came up at ${now.pretty}. " +
                "Something in the chain only carries USB 2 wires: the cable, an adapter, a hub or this phone's port.",
        )
    }
    if (now.isSuper) {
        if (link.source == SpeedSource.KERNEL && cap != null && cap.ordinal > now.ordinal) {
            return Verdict(
                Tone.WARN, "${now.nick} — not full speed",
                "Linked at ${now.pretty}; the device supports ${cap.pretty}. Part of the chain is limited to ${now.gen}.",
            )
        }
        val head = if (link.atLeast) "SuperSpeed link" else "${now.nick} link"
        return Verdict(
            Tone.GOOD, head,
            "The USB 3 lanes are connected end to end. Cable, adapter and port are all doing their job.",
        )
    }
    if (cap == null) {
        return Verdict(
            Tone.INFO, "${now.gen} link",
            "Running at ${now.pretty}. Allow access below to find out whether this device is USB 3 capable " +
                "and being held back.",
        )
    }
    return Verdict(
        Tone.INFO, "${now.gen} device",
        "The link matches what this device supports. Because it's a ${cap.gen} device, " +
            "it can't show whether the cable carries USB 3. Use a USB 3 drive for that.",
    )
}

fun storageVerdict(s: DeviceSnapshot): Verdict? {
    if (!s.isStorage) return null
    return when {
        s.hasUas -> Verdict(
            Tone.GOOD, "UASP supported",
            "USB Attached SCSI: commands are queued and run in parallel, so the drive is faster with less CPU load. " +
                "Android uses it when the phone's kernel includes the UAS driver.",
        )
        s.hasBot -> Verdict(
            Tone.WARN, "BOT only",
            "Legacy Bulk-Only Transport handles one command at a time. Fine for big copies, slower with many small files. " +
                "This comes from the drive's bridge chip, not the cable.",
        )
        else -> Verdict(Tone.INFO, "Mass storage", "Uses a less common storage protocol.")
    }
}

fun buildReport(device: DeviceSnapshot, d: DeepInfo?): String {
    val s = device.withDeep(d)
    val link = effectiveLink(s, d)
    val cap = capabilityOf(s, d, link)
    val v = linkVerdict(s, d, link, cap)
    return buildString {
        appendLine("${s.product}${s.manufacturer?.let { " — $it" } ?: ""}")
        appendLine("ID ${s.ids}   ${s.busLabel}")
        s.spec?.let { appendLine("Spec: USB $it") }
        appendLine("Link: ${link?.let { (if (it.atLeast) "≥ " else "") + it.speed.pretty + " (${it.speed.gen}) — ${it.source.text}" } ?: "unknown"}")
        appendLine("Capable of: ${cap?.let { "${it.pretty} (${it.gen})" } ?: "unknown"}")
        appendLine("Verdict: ${v.headline}")
        storageVerdict(s)?.let { appendLine("Storage: ${it.headline}") }
        powerText(s, d)?.let { appendLine("Max power: $it") }
        appendLine()
        s.interfaces.forEach { i ->
            append("Interface ${i.id}")
            if (i.alt > 0) append(" alt ${i.alt}")
            append(": ${className(i.cls)}")
            protocolDetail(i)?.let { append(" ($it)") }
            appendLine(String.format(Locale.US, "  [%02X/%02X/%02X]", i.cls, i.sub, i.proto))
            i.endpoints.forEach { e ->
                appendLine(String.format(Locale.US, "   EP 0x%02X %-3s %-4s %d B", e.address, if (e.isIn) "IN" else "OUT", e.typeName, e.maxPacket))
            }
        }
    }.trimEnd()
}
