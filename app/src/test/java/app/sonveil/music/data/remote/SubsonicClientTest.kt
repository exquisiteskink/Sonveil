package app.sonveil.music.data.remote

import app.sonveil.music.data.auth.AuthMode
import app.sonveil.music.data.auth.StoredCredentials
import kotlinx.coroutines.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class SubsonicClientTest {
    private val ok = """{"subsonic-response":{"status":"ok","version":"1.16.1"}}"""
    private fun failure(code: Int) = """{"subsonic-response":{"status":"failed","error":{"code":$code}}}"""

    @Test fun httpAuthenticationRequiresExplicitConsent(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.start()
            for (mode in listOf(AuthMode.Token, AuthMode.ApiKey)) {
                val client = SubsonicClient()
                val credentials = StoredCredentials(server.url("/").toString(), "user", "password", apiKey = "secret", authMode = mode)
                try {
                    client.login(credentials)
                    fail("HTTP login must require consent")
                } catch (_: SubsonicException) { }
                assertNull(client.credentials)
                client.credentials = credentials
                assertThrows(SubsonicException::class.java) { client.streamUrl("song") }
                assertThrows(SubsonicException::class.java) { client.downloadUrl("song") }
                assertThrows(SubsonicException::class.java) { client.coverUrl("cover", 400) }
            }
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun cancelledLyricsDoesNotStartFallbackRequest(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            server.start()
            val client = SubsonicClient()
            client.credentials = StoredCredentials(server.url("/").toString(), "user", "password", allowInsecureLanHttp = true)
            val job = launch(Dispatchers.Default) {
                try {
                    client.lyricsForSong(Song(id = "song", title = "Track", artist = "Artist"))
                    fail("Cancellation must propagate")
                } catch (_: CancellationException) { }
            }
            assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(3, TimeUnit.SECONDS) })
            withTimeout(2000) { job.cancelAndJoin() }
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun tokenLoginNeverSendsPasswordAndPublishesOnlyAfterSuccess() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(ok))
            server.start()
            val client = SubsonicClient()
            val creds = StoredCredentials(server.url("/music").toString(), "listener", "pässword", allowInsecureLanHttp = true)
            client.login(creds)
            val url = server.takeRequest().requestUrl!!
            assertEquals("/music/rest/ping", url.encodedPath)
            assertNull(url.queryParameter("p"))
            assertEquals(SubsonicClient.md5(creds.password + url.queryParameter("s")), url.queryParameter("t"))
            assertEquals(creds, client.credentials)
            server.enqueue(MockResponse().setBody(failure(40)))
            try { client.login(creds.copy(username = "other")); fail("Should reject login") } catch (_: SubsonicException) { }
            assertEquals(creds, client.credentials)
        }
    }

    @Test fun httpDoesNotAllowHexPasswordFallback(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(failure(41)))
            server.start()
            val client = SubsonicClient()
            try { client.login(StoredCredentials(server.url("/").toString(), "user", "password", allowInsecureLanHttp = true)); fail() }
            catch (e: SubsonicException) { assertEquals(41, e.code) }
            assertNull(client.credentials)
            assertEquals(1, server.requestCount)
            client.credentials = StoredCredentials(server.url("/").toString(), "user", "password", authMode = AuthMode.HexPassword, allowInsecureLanHttp = true)
            assertThrows(SubsonicException::class.java) { client.streamUrl("1") }
        }
    }

    @Test fun httpsFallbackFailureDoesNotLeaveCandidateCredentialsActive() = runBlocking {
        val cert = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(cert).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        MockWebServer().use { server ->
            server.useHttps(serverTls.sslSocketFactory(), false)
            server.enqueue(MockResponse().setBody(failure(41)))
            server.enqueue(MockResponse().setBody(failure(40)))
            server.start()
            val client = SubsonicClient(buildHttpClient().newBuilder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build())
            try { client.login(StoredCredentials(server.url("/").toString(), "user", "password")); fail() }
            catch (e: SubsonicException) { assertEquals(40, e.code) }
            assertNull(client.credentials)
            assertNull(server.takeRequest().requestUrl!!.queryParameter("p"))
            assertEquals("enc:" + SubsonicClient.toHex("password"), server.takeRequest().requestUrl!!.queryParameter("p"))
        }
    }

    @Test fun apiKeyAndOriginalQualityUseCorrectParameters() {
        val client = SubsonicClient()
        client.credentials = StoredCredentials("https://music.example", apiKey = "a&b", authMode = AuthMode.ApiKey)
        val original = client.streamUrl("song/a?b").toHttpUrl()
        assertEquals("raw", original.queryParameter("format"))
        assertEquals("a&b", original.queryParameter("apiKey"))
        assertEquals("song/a?b", original.queryParameter("id"))
        assertNull(original.queryParameter("u"))
        val compressed = client.streamUrl("1", 192).toHttpUrl()
        assertEquals("192", compressed.queryParameter("maxBitRate"))
        assertNull(compressed.queryParameter("format"))
        client.credentials = null
        assertNull(client.coverUrl("art"))

        client.credentials = StoredCredentials("https://music.example", apiKey = "a&b", authMode = AuthMode.ApiKey)
        val dl = client.downloadUrl("song/a?b").toHttpUrl()
        assertEquals("download", dl.pathSegments.last())
        assertEquals("song/a?b", dl.queryParameter("id"))
        assertEquals("a&b", dl.queryParameter("apiKey"))
        assertNull(dl.queryParameter("format"))
        client.credentials = null
    }

    @Test fun cancellingRequestStopsCallAndDoesNotPublishLogin() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            server.start()
            val client = SubsonicClient()
            val job = launch(Dispatchers.Default) { client.login(StoredCredentials(server.url("/").toString(), "user", "password", allowInsecureLanHttp = true)) }
            assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(3, TimeUnit.SECONDS) })
            withTimeout(2000) { job.cancelAndJoin() }
            assertNull(client.credentials)
            withTimeout(2000) { while (client.http.dispatcher.runningCallsCount() != 0) delay(10) }
        }
    }

    @Test fun oversizedChunkedResponsesAreRejected() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setChunkedBody("x".repeat(SubsonicClient.MAX_RESPONSE_BYTES.toInt() + 1), 8192))
            server.start()
            val client = SubsonicClient()
            try { client.login(StoredCredentials(server.url("/").toString(), "user", "password", allowInsecureLanHttp = true)); fail() }
            catch (e: SubsonicException) { assertEquals("Server response is too large", e.message) }
            assertNull(client.credentials)
        }
    }

    @Test fun optionalRequestsDoNotSwallowCancellation() = runBlocking {
        try { suspendRunCatching { throw CancellationException("cancel") }; fail() }
        catch (_: CancellationException) { }
    }

    @Test fun nullStringDoesNotBecomeLiteralNull() {
        assertEquals("", SubsonicClient().json.decodeFromString(FlexibleStringSerializer, "null"))
    }

    @Test fun biographyDoesNotReturnPreviousAccountsCachedValue() = runBlocking {
        val metadata = MetadataRepository(SubsonicClient())
        assertEquals("First account", metadata.biography("Artist", "First account"))
        assertEquals("Second account", metadata.biography("Artist", "Second account"))
    }
}
