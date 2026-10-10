package com.farhat.smartaccess

import com.farhat.smartaccess.device.Protocol
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProtocolTest {
    @Test fun commandsEncodeTheirParameters() {
        assertEquals("unlock", JSONObject(Protocol.unlock()).getString("action"))
        val relay = JSONObject(Protocol.relay(5, true))
        assertEquals(5, relay.getInt("index")); assertTrue(relay.getBoolean("on"))
        assertEquals(75, JSONObject(Protocol.fan(75)).getInt("speed"))
    }
    @Test fun rejectsUnsafeRanges() {
        for (i in listOf(0, 6)) assertThrows(IllegalArgumentException::class.java) { Protocol.relay(i, true) }
        for (i in listOf(-1, 101)) assertThrows(IllegalArgumentException::class.java) { Protocol.fan(i) }
    }
    @Test fun parsesGrantedAndDeniedRfidEvents() {
        val logs = Protocol.logs("""{"logs":[{"id":"a","uid":"ABC","granted":true,"timestamp":123},{"id":"b","uid":"DEF","granted":false,"timestamp":124}]}""")
        assertEquals(2, logs.size); assertTrue(logs[0].granted); assertFalse(logs[1].granted)
        assertEquals("a", logs[0].eventId); assertEquals(123L, logs[0].timestamp)
    }
    @Test fun statusRequiresFiveRelaysAndValidFan() {
        val status = Protocol.status("""{"relays":[true,false,false,true,false],"fan":40}""")
        assertEquals(5, status.relays.size); assertEquals(40, status.fan)
        assertThrows(IllegalArgumentException::class.java) { Protocol.status("""{"relays":[true],"fan":40}""") }
        assertThrows(IllegalArgumentException::class.java) { Protocol.status("""{"relays":[true,false,false,true,false],"fan":101}""") }
    }
}
