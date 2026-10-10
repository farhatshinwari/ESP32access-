package com.farhat.smartaccess

import com.farhat.smartaccess.device.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class WifiClientTest {
    @Test fun postsCommandAndChecksAcknowledgement() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"ok":true}"""))
            WifiClient().command(server.url("/").toString(), Protocol.relay(2, true))
            val request = server.takeRequest()
            assertEquals("/api/command", request.path); assertEquals("POST", request.method)
            assertEquals(Protocol.relay(2, true), request.body.readUtf8())
            server.enqueue(MockResponse().setBody("""{"ok":false}"""))
            try { WifiClient().command(server.url("/").toString(), Protocol.unlock()); fail("Rejection ignored") } catch (_: IllegalStateException) {}
        }
    }
    @Test fun readsStatusAndRfidHistoryFromDeviceEndpoints() = runBlocking {
        MockWebServer().use { server ->
            val base = server.url("/").toString()
            server.enqueue(MockResponse().setBody("""{"relays":[true,false,false,false,true],"fan":60}"""))
            val client = WifiClient()
            assertEquals(60, client.status(base).fan)
            assertEquals("/api/status", server.takeRequest().path)
            server.enqueue(MockResponse().setBody("""{"logs":[{"id":"event-1","uid":"ABCD","granted":false,"timestamp":1000}]}"""))
            val events = client.logs(base)
            assertEquals("/api/logs", server.takeRequest().path)
            assertEquals("ABCD", events.single().uid)
            assertFalse(events.single().granted)
        }
    }
    @Test fun httpFailureDoesNotBecomeSuccess() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503))
            try { WifiClient().status(server.url("/").toString()); fail("HTTP failure ignored") } catch (_: IllegalStateException) {}
        }
    }
}
