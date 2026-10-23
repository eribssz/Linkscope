package dev.linkscope

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ACTION_USB_PERMISSION = "dev.linkscope.USB_PERMISSION"

class MainActivity : ComponentActivity() {

    /** Bumped on every plug, unplug or permission answer to trigger a rescan. */
    private val tick = MutableStateFlow(0)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            tick.update { it + 1 }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        val usb = getSystemService(UsbManager::class.java)
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        setContent {
            LinkscopeTheme {
                LinkscopeApp(usb, tick, ::requestAccess)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        tick.update { it + 1 }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        super.onDestroy()
    }

    private fun requestAccess(device: UsbDevice) {
        val usb = getSystemService(UsbManager::class.java)
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        usb.requestPermission(device, PendingIntent.getBroadcast(this, 0, intent, flags))
    }
}

@Composable
fun LinkscopeApp(
    usb: UsbManager,
    tick: StateFlow<Int>,
    requestAccess: (UsbDevice) -> Unit,
) {
    val t by tick.collectAsState()
    var devices by remember { mutableStateOf(UsbScanner.scan(usb)) }

    // Rescan on every event, and poll gently as a safety net.
    LaunchedEffect(t) {
        while (true) {
            devices = withContext(Dispatchers.IO) { UsbScanner.scan(usb) }
            delay(1500)
        }
    }

    val deep = remember { mutableStateMapOf<String, DeepInfo>() }
    val scanning = remember { mutableStateMapOf<String, Boolean>() }
    // "dev:<usb path>" or "hist:<entry id>"; null = home.
    var screen by rememberSaveable { mutableStateOf<String?>(null) }
    // Plain holder (not state) so we can keep showing a device after it's unplugged.
    val lastSelected = remember { LastSeen() }
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val store = remember { HistoryStore(context.applicationContext) }
    var history by remember { mutableStateOf(store.load()) }
    /** Which history entry each currently connected device is writing to. */
    val sessions = remember { mutableMapOf<String, Long>() }

    fun persist(list: List<HistoryEntry>) {
        history = list
        scope.launch(Dispatchers.IO) { store.save(list) }
    }

    // Forget deep-scan results for unplugged devices (their path gets reused).
    LaunchedEffect(devices) {
        val present = devices.map { it.key }.toSet()
        deep.keys.filter { it !in present }.forEach { deep.remove(it) }
        sessions.keys.filter { it !in present }.forEach { sessions.remove(it) }
    }

    // Deep-scan every device we have access to, so history gets the full picture.
    LaunchedEffect(devices) {
        devices.filter { it.hasPermission && deep[it.key] == null && scanning[it.key] != true }.forEach { s ->
            scanning[s.key] = true
            scope.launch {
                val info = withContext(Dispatchers.IO) {
                    runCatching { DeepScanner.scan(usb, s.device) }.getOrElse {
                        DeepInfo(null, false, false, null, null, "Scan failed: ${it.message ?: it.javaClass.simpleName}")
                    }
                }
                deep[s.key] = info
                scanning.remove(s.key)
            }
        }
    }

    // Record each connection in history; refresh it when the deep scan lands.
    val deepSnapshot = deep.toMap()
    LaunchedEffect(devices, deepSnapshot) {
        var list = history
        var changed = false
        val now = System.currentTimeMillis()
        for (d in devices) {
            val id = sessions[d.key]
            if (id == null) {
                val fresh = entryFor(d, deepSnapshot[d.key], now, now, 1, now)
                val newest = list.firstOrNull()
                val entry = if (newest != null && newest.identity == fresh.identity &&
                    newest.link == fresh.link && newest.linkAtLeast == fresh.linkAtLeast
                ) {
                    fresh.copy(id = newest.id, firstSeen = newest.firstSeen, count = newest.count + 1)
                } else {
                    fresh
                }
                list = listOf(entry) + list.filter { it.id != entry.id }
                sessions[d.key] = entry.id
                changed = true
            } else {
                val old = list.firstOrNull { it.id == id } ?: continue // deleted while plugged in
                val upd = entryFor(d, deepSnapshot[d.key], old.id, old.firstSeen, old.count, old.lastSeen)
                if (upd != old) {
                    list = list.map { if (it.id == id) upd else it }
                    changed = true
                }
            }
        }
        if (changed) persist(list.sortedByDescending { it.lastSeen })
    }

    val selectedKey = screen?.removePrefix("dev:")?.takeIf { screen?.startsWith("dev:") == true }
    val selected = devices.firstOrNull { it.key == selectedKey }
    if (selected != null) lastSelected.value = selected

    BackHandler(enabled = screen != null) { screen = null }

    Box(Modifier.fillMaxSize().background(Ink.Bg)) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                if (targetState != null) {
                    (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 8 } + fadeOut())
                } else {
                    (slideInHorizontally { -it / 8 } + fadeIn()) togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
                }
            },
            label = "nav",
        ) { target ->
            val home: @Composable () -> Unit = {
                HomeScreen(
                    devices = devices,
                    deep = deep,
                    history = history,
                    onOpen = { screen = "dev:${it.key}" },
                    onOpenHistory = { screen = "hist:${it.id}" },
                    onClearHistory = { persist(emptyList()) },
                )
            }
            when {
                target == null -> home()
                target.startsWith("hist:") -> {
                    val e = history.firstOrNull { it.id.toString() == target.removePrefix("hist:") }
                    if (e == null) home() else HistoryDetailScreen(
                        e = e,
                        onBack = { screen = null },
                        onDelete = {
                            screen = null
                            persist(history.filter { it.id != e.id })
                        },
                    )
                }
                else -> {
                    val key = target.removePrefix("dev:")
                    val live = devices.firstOrNull { it.key == key }
                    val shown = live ?: lastSelected.value?.takeIf { it.key == key }
                    if (shown == null) home() else DetailScreen(
                        device = shown,
                        deep = deep[key],
                        scanning = scanning[key] == true,
                        connected = live != null,
                        onBack = { screen = null },
                        onDeepScan = { requestAccess(shown.device) },
                    )
                }
            }
        }
    }
}

private class LastSeen {
    var value: DeviceSnapshot? = null
}
