package app.sonveil.music.data.remote

import java.io.IOException
import java.net.InetAddress
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test

class NetworkSecurityTest {
    @Test fun artworkCannotTargetAnotherOrigin() {
        val server = "https://music.example/library"
        assertEquals("https://music.example/art?id=1", allowedArtworkUrl("https://music.example/art?id=1", server))
        for (url in listOf("http://music.example/art", "https://music.example:8443/art", "https://tracker.example/art",
                "http://127.0.0.1/action", "http://192.168.1.1/action", "https://user:pass@music.example/art", "file:///etc/passwd")) {
            assertNull(url, allowedArtworkUrl(url, server))
        }
        assertNull(allowedArtworkUrl("https://music.example/art", null))
        assertEquals("http://192.168.1.2/art", allowedArtworkUrl("http://192.168.1.2/art", "http://192.168.1.2"))
    }

    @Test fun publicNamesCannotMasqueradeAsPrivateIpv6() {
        listOf("fc-attacker.example", "fd.example", "8.8.8.8", "172.32.0.1", "2001:4860:4860::8888").forEach {
            assertFalse(it, isLanHost(it))
            assertThrows(SubsonicException::class.java) { requireAllowedServerUrl("http://$it".let { s ->
                if (':' in it) "http://[$it]/".toHttpUrl() else s.toHttpUrl()
            }) }
        }
        listOf("127.0.0.1", "10.0.0.2", "192.168.1.2", "172.16.0.1", "::1", "fd00::1", "fe90::1", "music.local").forEach {
            assertTrue(it, isLanHost(it))
        }
        assertFalse(isLanAddress(InetAddress.getByName("8.8.8.8")))
    }

    @Test fun baseUrlRejectsEmbeddedCredentialsAndAuthParameters() {
        listOf("https://user:pass@music.example", "https://music.example/?apiKey=secret", "https://music.example/#fragment").forEach {
            assertThrows(SubsonicException::class.java) { requireAllowedServerUrl(it.toHttpUrl()) }
        }
        requireAllowedServerUrl("https://music.example/subsonic/".toHttpUrl())
    }

    @Test fun redirectToDifferentPortNeverReceivesCredentials() {
        MockWebServer().use { source -> MockWebServer().use { destination ->
            destination.start()
            source.enqueue(MockResponse().setResponseCode(302).addHeader("Location", destination.url("/?apiKey=secret")))
            source.start()
            assertThrows(IOException::class.java) {
                buildHttpClient().newCall(Request.Builder().url(source.url("/?apiKey=secret")).build()).execute().close()
            }
            assertEquals(0, destination.requestCount)
        } }
    }

    @Test fun sameOriginRedirectWorksButLoopsAreBounded() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/next"))
            server.enqueue(MockResponse().setBody("ok"))
            server.start()
            val http = buildHttpClient()
            http.newCall(Request.Builder().url(server.url("/first")).build()).execute().use {
                assertEquals("ok", it.body!!.string())
            }
            repeat(6) { server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/loop")) }
            assertThrows(IOException::class.java) {
                http.newCall(Request.Builder().url(server.url("/loop")).build()).execute().close()
            }
            assertEquals(8, server.requestCount)
        }
    }

    @Test fun selfSignedCertificateIsRejected() {
        val cert = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val tls = HandshakeCertificates.Builder().heldCertificate(cert).build()
        MockWebServer().use { server ->
            server.useHttps(tls.sslSocketFactory(), false)
            server.start()
            assertThrows(IOException::class.java) {
                buildHttpClient().newCall(Request.Builder().url(server.url("/")).build()).execute().close()
            }
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun trustedCertificateWithWrongHostnameIsRejected() {
        val cert = HeldCertificate.Builder().addSubjectAlternativeName("wrong.example").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(cert).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        MockWebServer().use { server ->
            server.useHttps(serverTls.sslSocketFactory(), false)
            server.start()
            val client = buildHttpClient().newBuilder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build()
            assertThrows(IOException::class.java) {
                client.newCall(Request.Builder().url(server.url("/")).build()).execute().close()
            }
            assertEquals(0, server.requestCount)
        }
    }
}
