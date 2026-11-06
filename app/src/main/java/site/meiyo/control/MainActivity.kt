package site.meiyo.control

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val Ink = Color(0xFF100F16)
private val SurfaceA = Color(0xFF1B1924)
private val SurfaceB = Color(0xFF24212E)
private val Pink = Color(0xFFE7A7C5)
private val Lavender = Color(0xFFB9A8CC)
private val Muted = Color(0xFFA9A3B2)
private val Green = Color(0xFF8BD6A4)
private val Red = Color(0xFFE58A9B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MeiyoApp() }
    }
}

private data class BotState(
    val connected: Boolean = false,
    val running: Boolean = false,
    val pid: String = "—",
    val uptime: String = "—",
    val version: String = "—",
    val message: String = "Connecting to local bridge…"
)

@Composable
private fun MeiyoApp() {
    val scope = rememberCoroutineScope()
    var bot by remember { mutableStateOf(BotState()) }
    var logs by remember { mutableStateOf("Waiting for bridge logs…") }
    var busy by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(0) }
    var bridgeUrl by remember { mutableStateOf("http://127.0.0.1:8765") }
    var lastUpdated by remember { mutableStateOf("Not connected yet") }

    suspend fun call(path: String, method: String = "GET"): String = withContext(Dispatchers.IO) {
        val connection = (URL(bridgeUrl.trim().trimEnd('/') + path).openConnection() as HttpURLConnection)
        connection.requestMethod = method
        connection.connectTimeout = 3500
        connection.readTimeout = 4500
        connection.setRequestProperty("Accept", "application/json")
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("HTTP $code: $body")
            body
        } finally { connection.disconnect() }
    }

    suspend fun refresh() {
        val statusJson = JSONObject(call("/api/status"))
        val isRunning = statusJson.optBoolean("running", false)
        bot = BotState(
            connected = true,
            running = isRunning,
            pid = statusJson.opt("pid")?.toString()?.takeIf { it != "null" } ?: "—",
            uptime = formatUptime(statusJson.optLong("uptime", 0)),
            version = statusJson.optString("version", "—"),
            message = if (isRunning) "Meiyo is online and connected to Discord." else "Bridge connected. Meiyo is currently stopped."
        )
        val logJson = JSONObject(call("/api/logs"))
        val array = logJson.optJSONArray("logs")
        logs = if (array == null || array.length() == 0) "No logs yet." else
            (0 until array.length()).joinToString("\n") { index -> array.optString(index) }
        lastUpdated = "Updated just now"
    }

    fun action(path: String, label: String) {
        if (busy) return
        scope.launch {
            busy = true
            try {
                val result = JSONObject(call(path, "POST"))
                bot = bot.copy(message = result.optString("message", "$label request sent."))
                delay(500)
                refresh()
            } catch (e: Exception) {
                bot = bot.copy(connected = false, message = "Bridge error: ${e.message ?: "Connection failed"}")
            } finally { busy = false }
        }
    }

    LaunchedEffect(bridgeUrl) {
        while (true) {
            try { refresh() }
            catch (e: Exception) {
                bot = BotState(message = "Bridge offline. Start scripts/meiyo-control-start.sh in Termux. ${e.message ?: ""}")
                lastUpdated = "Waiting for connection"
            }
            delay(2500)
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Pink, secondary = Lavender, background = Ink, surface = SurfaceA,
        onPrimary = Color(0xFF352438), onBackground = Color(0xFFF4EFF5), onSurface = Color(0xFFF4EFF5)
    )) {
        Scaffold(
            containerColor = Ink,
            bottomBar = {
                NavigationBar(containerColor = SurfaceA, contentColor = Pink) {
                    NavigationBarItem(selected = page == 0, onClick = { page = 0 }, icon = { Icon(Icons.Rounded.Dashboard, null) }, label = { Text("Overview") })
                    NavigationBarItem(selected = page == 1, onClick = { page = 1 }, icon = { Icon(Icons.Rounded.Terminal, null) }, label = { Text("Console") })
                    NavigationBarItem(selected = page == 2, onClick = { page = 2 }, icon = { Icon(Icons.Rounded.Tune, null) }, label = { Text("Settings") })
                }
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    Image(painterResource(id = R.drawable.meiyo_logo), contentDescription = "Meiyo logo",
                        modifier = Modifier.size(58.dp).clip(RoundedCornerShape(17.dp)), contentScale = ContentScale.Crop)
                    Column(Modifier.weight(1f)) {
                        Text("MEIYO", color = Pink, fontSize = 27.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Text("COMPANION  ·  CONTROL CENTER", color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
                    }
                    Surface(color = SurfaceB, shape = CircleShape) {
                        Text("v1.1", color = Lavender, modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp), fontSize = 11.sp)
                    }
                }

                when (page) {
                    0 -> {
                        Card(colors = CardDefaults.cardColors(containerColor = SurfaceA), shape = RoundedCornerShape(24.dp)) {
                            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("BOT STATUS", color = Lavender, fontSize = 11.sp, letterSpacing = 1.7.sp, modifier = Modifier.weight(1f))
                                    Surface(color = if (bot.running) Color(0xFF20372C) else SurfaceB, shape = CircleShape) {
                                        Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                            Box(Modifier.size(7.dp).clip(CircleShape).background(if (bot.running) Green else if (bot.connected) Pink else Red))
                                            Text(if (!bot.connected) "BRIDGE OFFLINE" else if (bot.running) "ONLINE" else "STOPPED", color = if (bot.running) Green else Pink, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                                Text(if (bot.running) "Meiyo is running" else if (bot.connected) "Meiyo is stopped" else "Connecting to Termux", color = Color(0xFFF5EFF5), fontSize = 27.sp, fontWeight = FontWeight.Bold)
                                Text(bot.message, color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(onClick = { action("/api/start", "Start") }, enabled = !busy && bot.connected, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                                        Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(5.dp)); Text("Start")
                                    }
                                    OutlinedButton(onClick = { action("/api/stop", "Stop") }, enabled = !busy && bot.connected, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                                        Icon(Icons.Rounded.Stop, null); Spacer(Modifier.width(5.dp)); Text("Stop")
                                    }
                                }
                                OutlinedButton(onClick = { action("/api/restart", "Restart") }, enabled = !busy && bot.connected, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                                    Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Restart Meiyo")
                                }
                                OutlinedButton(onClick = { action("/api/register", "Command registration") }, enabled = !busy && bot.connected, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                                    Icon(Icons.Rounded.CloudDone, null); Spacer(Modifier.width(8.dp)); Text("Register slash commands")
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard("PROCESS ID", bot.pid, Modifier.weight(1f))
                            StatCard("BOT UPTIME", bot.uptime, Modifier.weight(1f))
                        }
                        Card(colors = CardDefaults.cardColors(containerColor = SurfaceA), shape = RoundedCornerShape(22.dp)) {
                            Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("LIVE CONSOLE", color = Pink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { page = 1 }) { Text("Open console") }
                                }
                                Text(logs.lines().takeLast(4).joinToString("\n").ifBlank { "Waiting for logs…" }, color = Color(0xFFB9D7C2), fontSize = 11.sp, lineHeight = 17.sp)
                            }
                        }
                        Text(lastUpdated, color = Muted, fontSize = 10.sp)
                    }
                    1 -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Live console", color = Pink, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                                Text("Recent output from your Termux bot process", color = Muted, fontSize = 12.sp)
                            }
                            TextButton(onClick = { scope.launch { runCatching { refresh() } } }) { Text("Refresh") }
                        }
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF13121A)), shape = RoundedCornerShape(18.dp)) {
                            Text(logs.ifBlank { "No logs yet." }, Modifier.fillMaxWidth().padding(16.dp), color = Color(0xFFB9D7C2), fontSize = 11.sp, lineHeight = 17.sp)
                        }
                    }
                    else -> {
                        Text("Settings", color = Pink, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Card(colors = CardDefaults.cardColors(containerColor = SurfaceA), shape = RoundedCornerShape(20.dp)) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("LOCAL CONTROL BRIDGE", color = Lavender, fontSize = 11.sp, letterSpacing = 1.2.sp)
                                OutlinedTextField(value = bridgeUrl, onValueChange = { bridgeUrl = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Bridge address") })
                                Text("Default: http://127.0.0.1:8765", color = Muted, fontSize = 11.sp)
                                Text("This app controls the existing Meiyo folder in Termux. Keep the bridge process running while using Companion.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                                Text("Bot version: ${bot.version}", color = Muted, fontSize = 12.sp)
                                TextButton(onClick = { scope.launch { runCatching { refresh() } } }) { Text("Test connection") }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = SurfaceA), shape = RoundedCornerShape(19.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(label, color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            Text(value, color = Color(0xFFF5EFF5), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun formatUptime(seconds: Long): String {
    if (seconds <= 0) return "—"
    val days = seconds / 86400
    val hours = (seconds % 86400) / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m ${seconds % 60}s"
    }
}
