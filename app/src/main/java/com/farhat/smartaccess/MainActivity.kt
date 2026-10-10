package com.farhat.smartaccess

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { MaterialTheme { SmartAccess() } } }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartAccess(vm: AccessViewModel = viewModel()) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var fan by remember(state.fan, state.busy) { mutableFloatStateOf(state.fan.toFloat()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) vm.paired() else vm.permissionDenied() }
    Scaffold(topBar = { TopAppBar(title = { Text("ESP32 Smart Access") }) }, bottomBar = {
        NavigationBar { listOf("Controls", "Access logs", "Connection").forEachIndexed { i, label ->
            NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Text(listOf("⌂", "≡", "↔")[i]) }, label = { Text(label) })
        } }
    }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(state.message, style = MaterialTheme.typography.bodyMedium); if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth()) }
            when (tab) {
                0 -> {
                    item { Text("Door access", style = MaterialTheme.typography.headlineSmall) }
                    item { Button(onClick = { confirm = "wifi" }, enabled = state.connected && !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Unlock over Wi-Fi") } }
                    item { OutlinedButton(onClick = { confirm = "bluetooth" }, enabled = state.address.isNotBlank() && !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Unlock over Bluetooth") } }
                    item { Text("Five relay outputs", style = MaterialTheme.typography.titleLarge) }
                    items(5) { index ->
                        Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Relay ${index + 1}")
                            Switch(checked = state.relays[index], onCheckedChange = { vm.relay(index + 1, it) }, enabled = state.connected && !state.busy)
                        } }
                    }
                    item { Text("Fan speed • ${fan.toInt()}%", style = MaterialTheme.typography.titleLarge)
                        Slider(value = fan, onValueChange = { fan = it }, onValueChangeFinished = { vm.fan(fan.toInt()) }, valueRange = 0f..100f, steps = 99, enabled = state.connected && !state.busy)
                        OutlinedButton(onClick = vm::refresh, enabled = !state.busy) { Text("Refresh device status") }
                    }
                }
                1 -> {
                    item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Button(onClick = vm::refresh, enabled = !state.busy) { Text("Sync RFID logs") }; TextButton(onClick = { confirm = "clear" }, enabled = !state.busy && logs.isNotEmpty()) { Text("Clear local") } } }
                    item { Text("${logs.size} stored events", style = MaterialTheme.typography.titleLarge) }
                    if (logs.isEmpty()) item { Text("No events yet. Sync with the ESP32 to import RFID access records.") }
                    items(logs, key = { it.eventId }) { log -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                        Text(log.uid, style = MaterialTheme.typography.titleMedium)
                        Text("${if (log.granted) "Granted" else "Denied"} • ${log.source}")
                        Text(DateFormat.getDateTimeInstance().format(Date(log.timestamp)), style = MaterialTheme.typography.bodySmall)
                    } } }
                }
                else -> {
                    item { Text("Wi-Fi connection", style = MaterialTheme.typography.headlineSmall); Text("Join the ESP32 network in Android settings. Enter its HTTP or HTTPS address.") }
                    item { OutlinedTextField(value = state.url, onValueChange = vm::url, label = { Text("ESP32 base URL") }, singleLine = true, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) }
                    item { TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }) { Text("Open Wi-Fi settings") } }
                    item { Button(onClick = vm::refresh, enabled = !state.busy) { Text("Connect & sync") }; Text(if (state.connected) "Wi-Fi device reachable" else "Wi-Fi device not verified") }
                    item { HorizontalDivider(); Text("Bluetooth connection", style = MaterialTheme.typography.headlineSmall); Text("Pair a Classic Bluetooth SPP ESP32 in Android settings, then load paired devices.") }
                    item { TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) { Text("Open Bluetooth settings") } }
                    item { OutlinedButton(onClick = { if (Build.VERSION.SDK_INT >= 31) permission.launch(Manifest.permission.BLUETOOTH_CONNECT) else vm.paired() }) { Text("Load paired devices") } }
                    items(state.devices, key = { it.second }) { device -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = state.address == device.second, onClick = { vm.select(device.second) }); Column { Text(device.first); Text(device.second, style = MaterialTheme.typography.bodySmall) } } }
                    item { Text("Selected: ${state.address.ifBlank { "None" }}"); Text("HTTP and Classic Bluetooth traffic is unencrypted. Use only a trusted private network and a paired device. The firmware must enforce access authorization.", style = MaterialTheme.typography.bodySmall) }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
    confirm?.let { action -> AlertDialog(onDismissRequest = { confirm = null }, title = { Text(if (action == "clear") "Clear local access logs?" else "Unlock the door?") }, text = { Text(if (action == "clear") "This deletes stored events on this phone only." else "Send an unlock command to the selected ESP32.") }, confirmButton = { TextButton(onClick = { confirm = null; if (action == "clear") vm.clear() else vm.unlock(action == "bluetooth") }) { Text("Confirm") } }, dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } }) }
}
