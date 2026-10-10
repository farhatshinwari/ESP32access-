package com.farhat.smartaccess

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.farhat.smartaccess.data.*
import com.farhat.smartaccess.device.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class UiState(
    val url: String = "http://192.168.4.1", val devices: List<Pair<String,String>> = emptyList(),
    val address: String = "", val connected: Boolean = false, val relays: List<Boolean> = List(5) { false },
    val fan: Int = 0, val busy: Boolean = false, val message: String = "Connect to your ESP32 Wi-Fi network, then refresh."
)
class AccessViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("device", 0)
    private val database = Room.databaseBuilder(app, AccessDatabase::class.java, "smart-access.db").build()
    private val wifi = WifiClient(app)
    private val bluetooth = BluetoothClient(app)
    private val mutable = MutableStateFlow(UiState(url = prefs.getString("url", "http://192.168.4.1")!!, address = prefs.getString("address", "")!!))
    val state = mutable.asStateFlow()
    val logs = database.logs().observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun url(value: String) { mutable.update { it.copy(url = value, connected = false) } }
    fun select(address: String) { mutable.update { it.copy(address = address) }; prefs.edit().putString("address", address).apply() }
    private fun operation(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true) }
        viewModelScope.launch {
            try { block() } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(message = e.message ?: "Device operation failed", connected = false) } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun permissionDenied() { mutable.update { it.copy(message = "Nearby devices permission is required for Bluetooth. Try again or enable it in app settings.") } }
    fun paired() { try { mutable.update { it.copy(devices = bluetooth.paired(), message = "Choose a paired ESP32. Pair new devices in Android settings.") } } catch (e: Exception) { mutable.update { it.copy(message = e.message ?: "Bluetooth permission required") } } }
    fun refresh() = operation {
        val base = mutable.value.url
        val status = wifi.status(base)
        database.logs().insert(wifi.logs(base))
        prefs.edit().putString("url", base).apply()
        mutable.update { it.copy(connected = true, relays = status.relays, fan = status.fan, message = "Device status and RFID logs synchronized") }
    }
    fun relay(index: Int, on: Boolean) = operation {
        wifi.command(mutable.value.url, Protocol.relay(index, on))
        val status = wifi.status(mutable.value.url)
        mutable.update { it.copy(relays = status.relays, fan = status.fan, message = "Relay $index updated") }
    }
    fun fan(speed: Int) = operation {
        wifi.command(mutable.value.url, Protocol.fan(speed))
        val status = wifi.status(mutable.value.url)
        mutable.update { it.copy(relays = status.relays, fan = status.fan, message = "Fan speed updated") }
    }
    fun unlock(viaBluetooth: Boolean) = operation {
        if (viaBluetooth) bluetooth.unlock(mutable.value.address) else wifi.command(mutable.value.url, Protocol.unlock())
        database.logs().insert(listOf(AccessLog(UUID.randomUUID().toString(), "Manual unlock", true, System.currentTimeMillis(), if (viaBluetooth) "Bluetooth" else "Wi-Fi")))
        mutable.update { it.copy(message = "Door unlock acknowledged by ESP32") }
    }
    fun clear() = operation { database.logs().clear(); mutable.update { it.copy(message = "Local logs cleared; ESP32 logs may return on the next sync") } }
    override fun onCleared() { bluetooth.close(); database.close(); super.onCleared() }
}
