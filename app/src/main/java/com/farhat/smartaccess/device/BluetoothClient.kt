package com.farhat.smartaccess.device

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import java.io.IOException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Paired Classic Bluetooth SPP. BLE-only ESP32 variants require a different transport. */
class BluetoothClient(context: Context) {
    private val manager = context.getSystemService(BluetoothManager::class.java)
    private val workers = Executors.newCachedThreadPool()
    @SuppressLint("MissingPermission")
    fun paired(): List<Pair<String, String>> {
        val adapter = manager?.adapter ?: error("Bluetooth is unavailable")
        check(adapter.isEnabled) { "Enable Bluetooth in Android settings" }
        return adapter.bondedDevices.map { (it.name ?: "ESP32") to it.address }.sortedBy { it.first }
    }
    @SuppressLint("MissingPermission")
    suspend fun unlock(address: String) = withContext(Dispatchers.IO) {
        val adapter = manager?.adapter ?: error("Bluetooth is unavailable")
        check(adapter.isEnabled) { "Enable Bluetooth in Android settings" }
        val socket: BluetoothSocket = adapter.getRemoteDevice(address).createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
        try {
            withTimeout(10000) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    continuation.invokeOnCancellation { runCatching { socket.close() } }
                    workers.execute {
                        try {
                            socket.connect()
                            socket.outputStream.write((Protocol.unlock() + "\n").toByteArray(Charsets.UTF_8))
                            socket.outputStream.flush()
                            val reply = StringBuilder()
                            while (reply.length < 4096) {
                                val ch = socket.inputStream.read()
                                check(ch != -1) { "Device disconnected without acknowledgement" }
                                if (ch == 10) break
                                reply.append(ch.toChar())
                            }
                            check(org.json.JSONObject(reply.toString()).getBoolean("ok")) { "Unlock rejected" }
                            if (continuation.isActive) continuation.resume(Unit)
                        } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            throw IOException("Bluetooth connection or acknowledgement timed out")
        } finally { runCatching { socket.close() } }
    }
    fun close() { workers.shutdownNow() }
}
