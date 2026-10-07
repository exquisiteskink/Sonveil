package app.sonveil.music.data.remote

import app.sonveil.music.data.auth.StoredCredentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.net.InetAddress
import okhttp3.Dns
import java.util.concurrent.TimeUnit

internal fun isLanAddress(address: InetAddress): Boolean =
    address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress ||
        (address.address.size == 16 && (address.address[0].toInt() and 0xfe) == 0xfc)

fun isLanHost(host: String): Boolean {
    val h = host.trim().lowercase().removePrefix("[").removeSuffix("]")
    if (h == "localhost" || h == "::1") return true
    if (h.endsWith(".local") || h.endsWith(".lan") || h.endsWith(".home") || h.endsWith(".internal")) return true
    val parts = h.split('.')
    if (parts.size == 4) {
        val nums = parts.map { it.toIntOrNull() ?: return false }
        if (nums.any { it !in 0..255 }) return false
        val a = nums[0]
        val b = nums[1]
        return a == 10 || a == 127 || (a == 192 && b == 168) || (a == 172 && b in 16..31) || (a == 169 && b == 254)
    }
    // Never mistake a DNS name beginning with fc/fd for a private IPv6 address.
    if (':' in h) return runCatching { isLanAddress(InetAddress.getByName(h)) }.getOrDefault(false)
    return false
}

fun requireAllowedServerUrl(url: HttpUrl) {
    if (url.username.isNotEmpty() || url.password.isNotEmpty() || url.query != null || url.fragment != null) {
        throw SubsonicException(0, "Enter a server URL without credentials, query parameters, or a fragment.")
    }
    when (url.scheme) {
        "https" -> Unit
        "http" -> if (!isLanHost(url.host)) {
            throw SubsonicException(
                0,
                "HTTP is only allowed for LAN servers (localhost, .local, or a private IP). Use HTTPS.",
            )
        }
        else -> throw SubsonicException(0, "Server URL must be http or https")
    }
}

fun requireAllowedAuthenticatedUrl(url: HttpUrl, credentials: StoredCredentials) {
    requireAllowedServerUrl(url)
    if (url.scheme == "http" && !credentials.allowInsecureLanHttp) {
        throw SubsonicException(0, "HTTPS is required. To use a LAN HTTP server, explicitly allow insecure HTTP on the sign-in screen.")
    }
}

/** Server-supplied artwork may only contact the configured music-server origin. */
fun allowedArtworkUrl(value: String?, serverUrl: String?): String? {
    val url = value?.let { runCatching { it.toHttpUrl() }.getOrNull() } ?: return null
    val server = serverUrl?.let { runCatching { it.toHttpUrl() }.getOrNull() } ?: return null
    return url.takeIf {
        it.username.isEmpty() && it.password.isEmpty() &&
            it.scheme == server.scheme && it.host == server.host && it.port == server.port
    }?.toString()
}

fun buildHttpClient(): OkHttpClient {
    return OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor(SameOriginRedirectInterceptor())
        // Keep OkHttp's platform certificate and hostname verification.
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = Dns.SYSTEM.lookup(hostname).also { addresses ->
                if (isLanHost(hostname) && addresses.any { !isLanAddress(it) }) {
                    throw java.net.UnknownHostException("LAN hostname resolved outside the local network")
                }
            }
        })
        .addNetworkInterceptor { chain ->
            val url = chain.request().url
            if (url.scheme == "http" &&
                (!isLanHost(url.host) || chain.connection()?.socket()?.inetAddress?.let(::isLanAddress) != true)
            ) throw java.io.IOException("Blocked cleartext request outside the local network")
            chain.proceed(chain.request())
        }
        .build()
}

internal class SameOriginRedirectInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        var response = chain.proceed(request)
        var hops = 0
        while (response.isRedirect) {
            hops++
            val location = response.header("Location") ?: break
            val next = request.url.resolve(location) ?: break
            if (!next.host.equals(request.url.host, ignoreCase = true) || next.scheme != request.url.scheme ||
                next.port != request.url.port || next.username.isNotEmpty() || next.password.isNotEmpty()
            ) {
                response.close()
                throw java.io.IOException("Blocked redirect from ${request.url.host} to ${next.scheme}://${next.host}")
            }
            response.close()
            if (hops > 5) throw java.io.IOException("Too many redirects")
            request = request.newBuilder().url(next).build()
            response = chain.proceed(request)
        }
        return response
    }
}
