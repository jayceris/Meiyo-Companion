package site.meiyo.control

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

private val bg = Color(0xFF101018)
private val panel = Color(0xFF1D1D2A)
private val pink = Color(0xFFE7A7C5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MeiyoScreen() }
    }
}

@Composable
fun MeiyoScreen() {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Connecting to Termux bridge…") }
    var logs by remember { mutableStateOf("Waiting for logs…") }
    var busy by remember { mutableStateOf(false) }
    var bridge by remember { mutableStateOf("http://127.0.0.1:8765") }

    suspend fun request(path: String): String = withContext(Dispatchers.IO) {
        val conn = URL(bridge.trimEnd('/') + path).openConnection() as HttpURLConnection
        conn.connectTimeout = 2500
        conn.readTimeout = 3500
        try { conn.inputStream.bufferedReader().use { it.readText() } }
        finally { conn.disconnect() }
    }
    fun act(path: String) {
        scope.launch {
            busy = true
            try { status = request(path) }
            catch (e: Exception) { status = "Bridge unavailable. Start it in Termux. (${e.message})" }
            busy = false
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            try { status = request("/status"); logs = request("/logs") }
            catch (_: Exception) { status = "Bridge offline. Start scripts/meiyo-control-start.sh in Termux." }
            delay(2500)
        }
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = pink, background = bg, surface = panel)) {
        Column(Modifier.fillMaxSize().background(bg).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("MEIYO", color = pink, fontSize = 30.sp)
            Text("CONTROL CENTER", color = Color.LightGray, fontSize = 12.sp)
            Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("BOT STATUS", color = pink, fontSize = 12.sp)
                    Text(status, color = Color.White)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { act("/start") }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("START") }
                        OutlinedButton(onClick = { act("/stop") }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("STOP") }
                    }
                    OutlinedButton(onClick = { act("/restart") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("RESTART") }
                    OutlinedButton(onClick = { act("/register") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("REGISTER COMMANDS") }
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("LIVE CONSOLE", color = pink)
                    Spacer(Modifier.height(8.dp))
                    Text(logs, color = Color(0xFFB8DCC1), fontSize = 11.sp)
                }
            }
            Text("Controls your existing Meiyo folder in Termux.", color = Color.Gray, fontSize = 11.sp)
        }
    }
}
