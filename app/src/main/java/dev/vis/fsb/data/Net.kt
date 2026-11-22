package dev.vis.fsb.data

import android.content.Context
import dev.vis.fsb.BuildConfig
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

object Net {
    const val BASE = "https://visiuun.com/fsb/"
    const val API = "https://visiuun.com/fsb/api/v1"

    lateinit var client: OkHttpClient
        private set

    lateinit var tokens: TokenStore
        private set

    /** Identifies the app to FSB, as the API docs ask. */
    val userAgent = "FSB-Android/${BuildConfig.VERSION_NAME} (+https://visiuun.com/fsb/)"

    @Synchronized
    fun init(context: Context) {
        if (::client.isInitialized) return
        val app = context.applicationContext
        tokens = TokenStore(app)
        client = OkHttpClient.Builder()
            .cache(Cache(File(app.cacheDir, "http"), 64L * 1024 * 1024))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request()
                val b = req.newBuilder()
                if (req.header("User-Agent") == null) b.header("User-Agent", userAgent)
                // The token only ever goes to the FSB API, over HTTPS, in the header (never the URL).
                if (req.url.isHttps && req.url.host == "visiuun.com" && req.url.encodedPath.startsWith("/fsb/api/")) {
                    if (req.header("Accept") == null) b.header("Accept", "application/json")
                    val t = tokens.token
                    if (t != null && req.header("Authorization") == null) b.header("Authorization", "Bearer $t")
                }
                chain.proceed(b.build())
            }
            .build()
    }

    /** Resolve relative site URLs such as `?ajax=avatar_proxy&target=x` against the FSB base. */
    fun abs(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("?") -> BASE + url
            url.startsWith("/") -> "https://visiuun.com$url"
            else -> BASE + url
        }
    }

    fun avatarProxy(handle: String) = "${BASE}?ajax=avatar_proxy&target=${java.net.URLEncoder.encode(handle, "UTF-8")}"
}
