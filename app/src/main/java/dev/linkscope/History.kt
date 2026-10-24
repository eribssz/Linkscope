package dev.linkscope

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * One past result: a device seen with a given link. Plugging the same device
 * in again with the same outcome bumps [count] instead of adding a new row,
 * so the list reads as "what changed" rather than a log of every replug.
 */
data class HistoryEntry(
    val id: Long,
    val firstSeen: Long,
    val lastSeen: Long,
    val count: Int,
    val identity: String,
    val product: String,
    val manufacturer: String?,
    val ids: String,
    val kind: DeviceKind,
    val serial: String?,
    val spec: String?,
    val link: LinkSpeed?,
    val linkAtLeast: Boolean,
    val linkSource: SpeedSource?,
    val capability: LinkSpeed?,
    val tone: Tone,
    val headline: String,
    val body: String,
    val storageTone: Tone?,
    val storageHeadline: String?,
    val power: String?,
    val report: String,
    val interfaces: List<InterfaceInfo> = emptyList(),
    val busLabel: String? = null,
    val path: String? = null,
) {
    val linkReading: SpeedReading?
        get() = link?.let { SpeedReading(it, linkSource ?: SpeedSource.ENDPOINTS, linkAtLeast) }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("firstSeen", firstSeen); put("lastSeen", lastSeen); put("count", count)
        put("identity", identity); put("product", product); putOpt("manufacturer", manufacturer)
        put("ids", ids); put("kind", kind.name); putOpt("serial", serial); putOpt("spec", spec)
        putOpt("link", link?.name); put("linkAtLeast", linkAtLeast); putOpt("linkSource", linkSource?.name)
        putOpt("capability", capability?.name); put("tone", tone.name)
        put("headline", headline); put("body", body)
        putOpt("storageTone", storageTone?.name); putOpt("storageHeadline", storageHeadline)
        putOpt("power", power); put("report", report)
        putOpt("busLabel", busLabel); putOpt("path", path)
        put("interfaces", JSONArray().apply {
            interfaces.forEach { i ->
                put(JSONObject().apply {
                    put("id", i.id); put("alt", i.alt); put("cls", i.cls); put("sub", i.sub); put("proto", i.proto)
                    putOpt("name", i.name)
                    put("eps", JSONArray().apply {
                        i.endpoints.forEach { e ->
                            put(JSONObject().apply {
                                put("a", e.address); put("in", e.isIn); put("t", e.type); put("mp", e.maxPacket); put("iv", e.interval)
                            })
                        }
                    })
                })
            }
        })
    }

    companion object {
        private fun JSONObject.str(k: String): String? = if (isNull(k)) null else optString(k).ifEmpty { null }
        private inline fun <reified E : Enum<E>> JSONObject.enum(k: String): E? =
            str(k)?.let { n -> enumValues<E>().firstOrNull { it.name == n } }

        private fun parseInterfaces(arr: JSONArray?): List<InterfaceInfo> {
            if (arr == null) return emptyList()
            return (0 until arr.length()).mapNotNull { k ->
                runCatching {
                    val i = arr.getJSONObject(k)
                    val eps = i.optJSONArray("eps") ?: JSONArray()
                    InterfaceInfo(
                        id = i.getInt("id"),
                        alt = i.optInt("alt"),
                        cls = i.getInt("cls"),
                        sub = i.optInt("sub"),
                        proto = i.optInt("proto"),
                        name = i.str("name"),
                        endpoints = (0 until eps.length()).map { n ->
                            val e = eps.getJSONObject(n)
                            EndpointInfo(e.getInt("a"), e.getBoolean("in"), e.getInt("t"), e.getInt("mp"), e.optInt("iv"))
                        },
                    )
                }.getOrNull()
            }
        }

        fun fromJson(o: JSONObject): HistoryEntry? = runCatching {
            HistoryEntry(
                id = o.getLong("id"),
                firstSeen = o.getLong("firstSeen"),
                lastSeen = o.getLong("lastSeen"),
                count = o.optInt("count", 1),
                identity = o.getString("identity"),
                product = o.getString("product"),
                manufacturer = o.str("manufacturer"),
                ids = o.getString("ids"),
                kind = o.enum<DeviceKind>("kind") ?: DeviceKind.OTHER,
                serial = o.str("serial"),
                spec = o.str("spec"),
                link = o.enum<LinkSpeed>("link"),
                linkAtLeast = o.optBoolean("linkAtLeast"),
                linkSource = o.enum<SpeedSource>("linkSource"),
                capability = o.enum<LinkSpeed>("capability"),
                tone = o.enum<Tone>("tone") ?: Tone.MUTED,
                headline = o.getString("headline"),
                body = o.optString("body"),
                storageTone = o.enum<Tone>("storageTone"),
                storageHeadline = o.str("storageHeadline"),
                power = o.str("power"),
                report = o.optString("report"),
                interfaces = parseInterfaces(o.optJSONArray("interfaces")),
                busLabel = o.str("busLabel"),
                path = o.str("path"),
            )
        }.getOrNull()
    }
}

/** Saved as a small JSON file in the app's private storage. */
class HistoryStore(context: Context) {
    private val file = File(context.filesDir, "history.json")

    fun load(): List<HistoryEntry> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).mapNotNull { HistoryEntry.fromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList()).sortedByDescending { it.lastSeen }

    fun save(entries: List<HistoryEntry>) {
        runCatching {
            val arr = JSONArray()
            entries.forEach { arr.put(it.toJson()) }
            val tmp = File(file.parentFile, "history.json.tmp")
            tmp.writeText(arr.toString())
            tmp.renameTo(file)
        }
    }
}

fun identityOf(s: DeviceSnapshot): String = "${s.ids}|${s.product}"

/** Build (or refresh) the history entry for a device as it looks right now. */
fun entryFor(device: DeviceSnapshot, deep: DeepInfo?, id: Long, firstSeen: Long, count: Int, now: Long): HistoryEntry {
    val s = device.withDeep(deep)
    val link = effectiveLink(s, deep)
    val cap = capabilityOf(s, deep, link)
    val v = linkVerdict(s, deep, link, cap)
    val storage = storageVerdict(s)
    return HistoryEntry(
        id = id,
        firstSeen = firstSeen,
        lastSeen = now,
        count = count,
        identity = identityOf(device),
        product = s.product,
        manufacturer = s.manufacturer,
        ids = s.ids,
        kind = s.kind,
        serial = deep?.serial,
        spec = s.spec,
        link = link?.speed,
        linkAtLeast = link?.atLeast ?: false,
        linkSource = link?.source,
        capability = cap,
        tone = v.tone,
        headline = v.headline,
        body = v.body,
        storageTone = storage?.tone,
        storageHeadline = storage?.headline,
        power = powerText(s, deep),
        report = buildReport(device, deep),
        interfaces = s.interfaces,
        busLabel = s.busLabel,
        path = s.key,
    )
}
