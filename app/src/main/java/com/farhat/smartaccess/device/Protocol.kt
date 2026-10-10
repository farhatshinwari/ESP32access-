package com.farhat.smartaccess.device

import com.farhat.smartaccess.data.AccessLog
import org.json.JSONObject

object Protocol {
    fun unlock() = JSONObject().put("action", "unlock").toString()
    fun relay(index: Int, on: Boolean): String {
        require(index in 1..5)
        return JSONObject().put("action", "relay").put("index", index).put("on", on).toString()
    }
    fun fan(speed: Int): String {
        require(speed in 0..100)
        return JSONObject().put("action", "fan").put("speed", speed).toString()
    }
    fun logs(json: String): List<AccessLog> {
        val array = JSONObject(json).getJSONArray("logs")
        return (0 until array.length()).map { i ->
            val event = array.getJSONObject(i)
            AccessLog(event.getString("id"), event.getString("uid"), event.getBoolean("granted"), event.getLong("timestamp"), "RFID")
        }
    }
    fun status(json: String): DeviceStatus {
        val obj = JSONObject(json)
        val relays = obj.getJSONArray("relays")
        require(relays.length() == 5) { "ESP32 must report five relays" }
        val fan = obj.getInt("fan")
        require(fan in 0..100)
        return DeviceStatus(List(5) { relays.getBoolean(it) }, fan)
    }
}
data class DeviceStatus(val relays: List<Boolean>, val fan: Int)
