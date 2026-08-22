package com.memoria.mobile.data.remote

import com.memoria.mobile.BuildConfig
import com.squareup.moshi.Moshi
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Mutable session shared with the OkHttp interceptor so the bearer token can
 * change (login/logout) without rebuilding the client.
 */
class SessionState {
    @Volatile
    var token: String? = null
}

/**
 * Resolver that survives an outage of the wildcard-DNS service the default
 * backend address depends on.
 *
 * `35.247.217.66.nip.io` *contains* the address it resolves to, so when the
 * system resolver fails there is no need to give up: we read the IP straight out
 * of the hostname. TLS is untouched — the connection still sends the original
 * hostname in SNI and still verifies the certificate against it, so this is a
 * fallback for a broken resolver, not a way around certificate checks.
 */
internal class WildcardDnsFallback(private val delegate: Dns = Dns.SYSTEM) : Dns {

    override fun lookup(hostname: String): List<InetAddress> = try {
        delegate.lookup(hostname)
    } catch (e: UnknownHostException) {
        embeddedAddress(hostname)?.let { listOf(it) } ?: throw e
    }

    /** Pulls the IPv4 address encoded in a `<ip>.nip.io` / `<ip>.sslip.io` name. */
    private fun embeddedAddress(hostname: String): InetAddress? {
        val suffix = SUFFIXES.firstOrNull { hostname.endsWith(it, ignoreCase = true) } ?: return null
        val quad = hostname.dropLast(suffix.length)
            .split('.', '-')
            .filter { it.isNotEmpty() }
            .takeLast(4)
        if (quad.size != 4) return null
        val octets = ByteArray(4)
        quad.forEachIndexed { i, label ->
            val value = label.toIntOrNull() ?: return null
            if (value !in 0..255) return null
            octets[i] = value.toByte()
        }
        // getByAddress(host, bytes) attaches the name without resolving it.
        return runCatching { InetAddress.getByAddress(hostname, octets) }.getOrNull()
    }

    private companion object {
        /** Wildcard-DNS services that encode the target IP in the name itself. */
        val SUFFIXES = listOf(".nip.io", ".sslip.io")
    }
}

/**
 * Builds (and caches) a Retrofit [ApiService] for the current backend base URL.
 * The URL is user-overridable at runtime, so we rebuild only when it changes.
 */
class ApiProvider(private val session: SessionState) {

    private val moshi: Moshi = Moshi.Builder().build()

    private var cachedRoot: String? = null
    private var cachedService: ApiService? = null

    @Synchronized
    fun service(rootUrl: String): ApiService {
        val root = normalizeRoot(rootUrl)
        val existing = cachedService
        if (existing != null && root == cachedRoot) return existing

        // callTimeout is the only knob that bounds a whole call including DNS;
        // without it a stalled network left the button spinning for over a
        // minute. Every phase is capped, and the total is capped below the sum.
        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor())
            .addInterceptor(loggingInterceptor())
            .dns(WildcardDnsFallback())
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("$root/api/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        val service = retrofit.create(ApiService::class.java)
        cachedRoot = root
        cachedService = service
        return service
    }

    /** `<root>/health` — outside the /api prefix. */
    fun healthUrl(rootUrl: String): String = "${normalizeRoot(rootUrl)}/health"

    private fun authInterceptor() = Interceptor { chain ->
        val builder = chain.request().newBuilder()
        session.token?.takeIf { it.isNotBlank() }?.let {
            builder.header("Authorization", "Bearer $it")
        }
        chain.proceed(builder.build())
    }

    private fun loggingInterceptor(): HttpLoggingInterceptor {
        val logging = HttpLoggingInterceptor()
        logging.level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BASIC
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
        return logging
    }

    companion object {
        /** Strips a trailing slash and any trailing `/api`, matching the web app. */
        fun normalizeRoot(raw: String): String {
            var s = raw.trim().ifEmpty { BuildConfig.DEFAULT_API_BASE_URL }
            s = s.trimEnd('/')
            if (s.endsWith("/api")) s = s.dropLast(4).trimEnd('/')
            return s
        }
    }
}
