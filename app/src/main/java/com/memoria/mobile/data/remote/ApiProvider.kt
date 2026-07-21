package com.memoria.mobile.data.remote

import com.memoria.mobile.BuildConfig
import com.squareup.moshi.Moshi
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
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

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor())
            .addInterceptor(loggingInterceptor())
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
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
