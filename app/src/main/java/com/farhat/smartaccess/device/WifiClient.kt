package com.farhat.smartaccess.device

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class WifiClient(private val context: Context? = null) {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).callTimeout(10, TimeUnit.SECONDS).build()
    private fun endpoint(base: String, path: String): HttpUrl {
        val url = base.trim().toHttpUrl()
        require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) { "Use a device URL without credentials or query parameters" }
        return url.newBuilder().encodedPath(path).build()
    }
    private suspend fun request(base: String, path: String, body: String? = null): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(endpoint(base, path))
        if (body != null) builder.post(body.toRequestBody("application/json".toMediaType()))
        // Bind to Wi-Fi even when Android prefers cellular on an ESP32 AP without Internet.
        val requestClient = if (context != null) {
            val connectivity = context.getSystemService(ConnectivityManager::class.java)
            val network = connectivity.allNetworks.firstOrNull {
                connectivity.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            } ?: error("Connect the phone to the ESP32 Wi-Fi network first")
            client.newBuilder().socketFactory(network.socketFactory)
                .dns(object : Dns { override fun lookup(hostname: String) = network.getAllByName(hostname).toList() }).build()
        } else client
        requestClient.newCall(builder.build()).execute().use { response ->
            check(response.isSuccessful) { "ESP32 returned HTTP ${response.code}" }
            response.body?.string() ?: error("Empty device response")
        }
    }
    suspend fun status(base: String) = Protocol.status(request(base, "/api/status"))
    suspend fun logs(base: String) = Protocol.logs(request(base, "/api/logs"))
    suspend fun command(base: String, json: String) {
        val result = org.json.JSONObject(request(base, "/api/command", json))
        check(result.getBoolean("ok")) { "Device rejected command" }
    }
}
