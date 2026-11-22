package dev.vis.fsb.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** Client for the documented FSB API v1 — https://visiuun.com/fsb/docs/ */
object FsbApi {
    private const val API = Net.API
    private val JSON = "application/json; charset=utf-8".toMediaType()
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    // ---------------------------------------------------------------- transport

    /** Performs a request and unwraps the `{ok, data}` envelope, mapping errors to typed exceptions. */
    private suspend fun call(method: String, path: String, body: JSONObject? = null): Any? = withContext(Dispatchers.IO) {
        val rb: RequestBody? = when {
            body != null -> body.toString().toRequestBody(JSON)
            method == "GET" -> null
            else -> ByteArray(0).toRequestBody(null)
        }
        val req = Request.Builder().url(API + path).method(method, rb).build()
        Net.client.newCall(req).execute().use { resp ->
            val text = resp.body.string()
            val o = runCatching { JSONObject(text) }.getOrNull()
            if (o != null && o.optBoolean("ok")) return@withContext o.opt("data")
            val err = o?.optJSONObject("error")
            val code = err?.optString("code").orEmpty()
            val msg = err?.optString("message").orEmpty()
            throw when {
                resp.code == 429 || code == "rate_limited" ->
                    RateLimitedException(resp.header("Retry-After")?.toIntOrNull() ?: 60)
                resp.code == 401 || code == "invalid_token" || code == "auth_required" ->
                    AuthException(code.ifBlank { "auth_required" }, msg.ifBlank { "Please sign in again" })
                resp.code == 404 || code == "not_found" ->
                    NotFoundException(msg.ifBlank { "Account not found, deleted or suspended" })
                o == null -> ApiException(
                    "bad_response",
                    if (resp.code >= 500) "FSB is temporarily unavailable (${resp.code})" else "Unexpected response (${resp.code})",
                    resp.code,
                )
                else -> ApiException(code, msg.ifBlank { "Request failed (${resp.code})" }, resp.code)
            }
        }
    }

    private suspend fun obj(method: String, path: String, body: JSONObject? = null): JSONObject =
        call(method, path, body) as? JSONObject ?: JSONObject()

    // ---------------------------------------------------------------- auth (device flow)

    suspend fun startDeviceFlow(): DeviceCode {
        val d = obj("POST", "/auth/device", JSONObject().put("client_name", "FSB for Android").put("scope", "read write"))
        return DeviceCode(
            deviceCode = d.getString("device_code"),
            userCode = d.getString("user_code"),
            verificationUri = d.optString("verification_uri", "https://visiuun.com/fsb/apps"),
            verificationUriComplete = d.optString("verification_uri_complete").ifBlank { d.optString("verification_uri", "https://visiuun.com/fsb/apps") },
            expiresAt = System.currentTimeMillis() + d.optInt("expires_in", 600) * 1000L,
            interval = d.optInt("interval", 5).coerceAtLeast(1),
        )
    }

    /** Returns the access token, or null while the user hasn't approved yet (`authorization_pending` / `slow_down`). */
    suspend fun pollDeviceToken(deviceCode: String): String? = try {
        obj("POST", "/auth/token", JSONObject().put("device_code", deviceCode)).getString("access_token")
    } catch (e: ApiException) {
        if (e.code == "authorization_pending" || e.code == "slow_down") {
            if (e.code == "slow_down") throw SlowDown()
            null
        } else throw e
    }

    class SlowDown : Exception()

    suspend fun revokeCurrentToken() {
        call("DELETE", "/me/tokens/current")
    }

    // ---------------------------------------------------------------- account

    suspend fun me(): Account {
        val d = obj("GET", "/me")
        val user = d.optJSONObject("user") ?: d.optJSONObject("account") ?: d
        // /me's exact layout isn't documented, so look for linked accounts anywhere in the response.
        val xHandle = deepFind(d) { path, key ->
            val underX = path.any { it in X_KEYS || it.startsWith("x_") || it.startsWith("twitter") }
            (underX && key in HANDLE_KEYS) || key in X_HANDLE_KEYS
        }?.removePrefix("@")?.takeIf { it.matches(Regex("[A-Za-z0-9_]{1,15}")) }
        val discordName = deepFind(d) { path, key ->
            (path.any { it.contains("discord") } && key in DISCORD_NAME_KEYS) || key == "discord_name" || key == "discord_username"
        }
        val sbJson = deepObject(d) { path, key -> key == "shadowban" || key == "x_shadowban" || (key == "status" && path.any { it in X_KEYS }) }
        val sb = sbJson?.takeIf { it.has("tests") || it.has("verdict") }?.let(::parseShadowban)
        val settings = d.optJSONObject("settings")
        val email = deepFind(d) { _, key -> key == "email" }
        return Account(
            name = user.str("display_name") ?: user.str("name") ?: user.str("username") ?: discordName ?: xHandle?.let { "@$it" } ?: "FSB user",
            avatar = Net.abs(user.str("avatar") ?: user.str("avatar_url") ?: deepFind(d) { _, key -> key == "avatar" || key == "avatar_url" })
                ?: xHandle?.let(Net::avatarProxy),
            email = email,
            xHandle = xHandle,
            discordName = discordName,
            scopes = (d.optJSONArray("scopes") ?: d.optJSONObject("token")?.optJSONArray("scopes")).strings(),
            hidePresence = settings?.takeIf { it.has("hide_presence") && !it.isNull("hide_presence") }?.optBoolean("hide_presence"),
            xShadowban = sb,
        )
    }

    private val X_KEYS = setOf("x", "twitter", "x_account", "twitter_account", "linked_x")
    private val HANDLE_KEYS = setOf("username", "handle", "screen_name")
    private val X_HANDLE_KEYS = setOf("x_handle", "x_username", "twitter_handle", "twitter_username", "x_screen_name")
    private val DISCORD_NAME_KEYS = setOf("global_name", "display_name", "name", "username")

    /** Breadth-first search for the first non-blank string whose (ancestor path, key) matches. */
    private fun deepFind(root: JSONObject, match: (List<String>, String) -> Boolean): String? {
        val queue = ArrayDeque<Pair<List<String>, JSONObject>>().apply { add(emptyList<String>() to root) }
        while (queue.isNotEmpty()) {
            val (path, o) = queue.removeFirst()
            for (k in o.keys()) {
                val key = k.lowercase()
                when (val v = o.opt(k)) {
                    is JSONObject -> queue.add(path + key to v)
                    is String -> if (v.isNotBlank() && v != "null" && match(path, key)) return v
                }
            }
        }
        return null
    }

    private fun deepObject(root: JSONObject, match: (List<String>, String) -> Boolean): JSONObject? {
        val queue = ArrayDeque<Pair<List<String>, JSONObject>>().apply { add(emptyList<String>() to root) }
        while (queue.isNotEmpty()) {
            val (path, o) = queue.removeFirst()
            for (k in o.keys()) {
                val v = o.opt(k) as? JSONObject ?: continue
                if (match(path, k.lowercase())) return v
                queue.add(path + k.lowercase() to v)
            }
        }
        return null
    }

    suspend fun setHidePresence(hide: Boolean) {
        call("PATCH", "/me/settings", JSONObject().put("hide_presence", hide))
    }

    suspend fun tokens(): List<AppToken> {
        val d = call("GET", "/me/tokens")
        val arr = d as? JSONArray ?: (d as? JSONObject)?.let { it.optJSONArray("tokens") ?: it.optJSONArray("items") } ?: JSONArray()
        return arr.objects().map { t ->
            AppToken(
                name = t.str("name") ?: t.str("client_name") ?: t.str("label") ?: "Token",
                kind = t.str("type") ?: t.str("kind") ?: "",
                scopes = t.optJSONArray("scopes").strings().ifEmpty { t.str("scope")?.split(' ').orEmpty() },
                lastUsed = t.optLong("last_used", t.optLong("last_used_at")),
                current = t.optBoolean("current"),
            )
        }
    }

    // ---------------------------------------------------------------- favorites & tracking

    suspend fun favorites(): List<ProfileSummary> = summaryList(call("GET", "/me/favorites"), "favorites")

    suspend fun setFavorite(username: String, on: Boolean) {
        call(if (on) "PUT" else "DELETE", "/me/favorites/${enc(username)}")
    }

    suspend fun tracked(): List<ProfileSummary> = summaryList(call("GET", "/me/tracked"), "tracked")

    suspend fun setTracked(username: String, on: Boolean) {
        if (on) call("POST", "/me/tracked", JSONObject().put("username", username))
        else call("DELETE", "/me/tracked/${enc(username)}")
    }

    private fun summaryList(d: Any?, key: String): List<ProfileSummary> {
        val arr = d as? JSONArray
            ?: (d as? JSONObject)?.let { it.optJSONArray(key) ?: it.optJSONArray("profiles") ?: it.optJSONArray("items") ?: it.optJSONArray("results") }
            ?: JSONArray()
        return arr.objects().mapNotNull(::parseSummary)
    }

    // ---------------------------------------------------------------- explore, search, random

    suspend fun explore(page: Int, status: String = "all", sort: String = "recent", limit: Int = 30): ExplorePage {
        val d = obj("GET", "/explore?status=${enc(status)}&sort=${enc(sort)}&page=$page&limit=$limit")
        val arr = d.optJSONArray("profiles") ?: JSONArray()
        val items = arr.objects().mapNotNull(::parseSummary)
        return ExplorePage(items, d.optBoolean("has_more", arr.length() >= limit), d.optInt("total", items.size))
    }

    suspend fun search(q: String, loose: Boolean = true): List<ProfileSummary> {
        val d = obj("GET", "/search?q=${enc(q)}${if (loose) "&loose=1" else ""}")
        return (d.optJSONArray("results") ?: JSONArray()).objects().mapNotNull(::parseSummary)
    }

    suspend fun random(status: String = "all", exclude: String? = null): String? {
        val d = obj("GET", "/random?status=${enc(status)}${exclude?.let { "&exclude=${enc(it)}" } ?: ""}")
        return d.str("username")
    }

    // ---------------------------------------------------------------- profiles

    suspend fun profile(username: String): Profile {
        val d = obj("GET", "/profiles/${enc(username)}?include=history")
        val p = d.optJSONObject("profile") ?: throw ApiException("bad_response", "Unexpected response for @$username")
        val stats = d.optJSONObject("stats")
        val sn = p.str("username") ?: username
        val history = (d.optJSONArray("history") ?: JSONArray()).objects().mapNotNull { h ->
            val score = if (h.isNull("score")) return@mapNotNull null else h.optInt("score", -1)
            if (score < 0) return@mapNotNull null
            HistoryPoint(h.optLong("time"), h.optString("status"), score, h.str("active_restrictions") ?: h.str("active_bans") ?: "")
        }.sortedBy { it.time }
        return Profile(
            screenName = sn,
            name = p.str("name") ?: sn,
            avatar = Net.abs(p.str("avatar")),
            banner = banner(p),
            bio = p.str("bio").orEmpty(),
            location = p.str("location").orEmpty(),
            createdAt = p.str("joined").orEmpty(),
            followers = p.optLong("followers"),
            following = p.optLong("following"),
            posts = p.optLong("posts"),
            likes = p.optLong("likes"),
            verified = p.optBoolean("verified"),
            url = p.str("url") ?: "https://x.com/$sn",
            fsbUrl = p.str("fsb_url") ?: profileUrl(sn),
            shadowban = d.optJSONObject("shadowban")?.let(::parseShadowban) ?: unknownShadowban(),
            history = history,
            trackedBy = stats?.optInt("tracked_by") ?: 0,
            favoritedBy = stats?.optInt("favorited_by") ?: 0,
            pageViews = stats?.optInt("page_views") ?: 0,
            favorited = d.bool("favorited") ?: p.bool("favorited"),
        )
    }

    /** Recent posts and reposts with direct media URLs. */
    suspend fun posts(username: String, author: Profile?, limit: Int = 100): List<Post> {
        val d = obj("GET", "/profiles/${enc(username)}/posts?limit=$limit")
        val owner = d.str("username") ?: author?.screenName ?: username
        return (d.optJSONArray("posts") ?: JSONArray()).objects().mapNotNull { p ->
            val id = p.str("id") ?: return@mapNotNull null
            val repostOf = p.str("repost_of")
            val isRepost = p.optBoolean("is_repost") || repostOf != null
            val handle = if (isRepost && repostOf != null) repostOf else owner
            val own = handle.equals(owner, true)
            Post(
                id = id,
                url = p.str("url") ?: "https://x.com/$owner/status/$id",
                text = p.str("text").orEmpty(),
                createdTs = p.optLong("timestamp").takeIf { it > 0 } ?: twitterTime(p.optString("created_at")),
                likes = p.optLong("likes"),
                retweets = p.optLong("reposts"),
                replies = p.optLong("replies"),
                views = p.optLong("views"),
                pinned = p.optBoolean("is_pinned"),
                isRepost = isRepost,
                repostOf = repostOf,
                authorName = if (own) author?.name ?: owner else handle,
                authorHandle = handle,
                authorAvatar = if (own) author?.avatar else null,
                verified = own && author?.verified == true,
                sensitive = p.optBoolean("possibly_sensitive"),
                media = parseMedia(p.optJSONArray("media")),
                quote = p.optJSONObject("quote")?.let { q ->
                    val qid = q.str("id") ?: return@let null
                    Quote(qid, q.str("username") ?: "", q.str("text").orEmpty(), parseMedia(q.optJSONArray("media")))
                },
            )
        }.distinctBy { it.id }
    }

    private fun parseMedia(arr: JSONArray?): List<Media> = arr.objects().mapNotNull { m ->
        val url = m.str("url") ?: return@mapNotNull null
        Media(m.optString("type", "photo"), url, m.str("thumbnail_url") ?: url)
    }

    /** Timeline insights computed from the posts the API returned. */
    fun insights(posts: List<Post>): Insights? {
        val own = posts.filter { !it.isRepost }
        if (own.isEmpty()) return null
        val n = own.size.toDouble()
        fun eng(p: Post) = p.likes + p.retweets + p.replies
        val withViews = own.filter { it.views > 0 }
        val linkRx = Regex("""https?://(?!t\.co/)\S+|https://t\.co/\S+""")
        val linkPosts = own.count { p -> p.media.isEmpty() && linkRx.containsMatchIn(p.text) }
        fun outlier(label: String, p: Post?) = p?.let { Outlier(label, it.url, it.text, it.likes, it.retweets, it.replies, it.views) }
        return Insights(
            sampleSize = own.size,
            avgLikes = own.sumOf { it.likes } / n,
            avgRetweets = own.sumOf { it.retweets } / n,
            avgReplies = own.sumOf { it.replies } / n,
            avgViews = own.sumOf { it.views } / n,
            engagementRate = if (withViews.isEmpty()) 0.0 else withViews.sumOf { eng(it) }.toDouble() / withViews.sumOf { it.views } * 100,
            linkPenalty = linkPosts / n * 100,
            mediaRichness = own.count { it.media.isNotEmpty() } / n * 100,
            outliers = listOfNotNull(
                outlier("Best performing", own.maxByOrNull { it.likes }),
                outlier("Most viewed", own.maxByOrNull { it.views }),
                outlier("Most discussed", own.maxByOrNull { it.replies }),
                outlier("Lowest performing", own.filter { it.views > 0 }.minByOrNull { eng(it).toDouble() / it.views }),
            ),
        )
    }

    // ---------------------------------------------------------------- parsing helpers

    private fun parseSummary(o0: JSONObject): ProfileSummary? {
        val o = o0.optJSONObject("profile") ?: o0
        val user = o.str("username") ?: o.str("screen_name") ?: o0.str("username") ?: return null
        return ProfileSummary(
            username = user,
            name = o.str("name") ?: user,
            avatar = Net.abs(o.str("avatar")),
            banner = banner(o) ?: banner(o0),
            verified = o.optBoolean("verified"),
            followers = o.optLong("followers"),
            accent = o.str("accent_color"),
            shadowban = (o0.optJSONObject("shadowban") ?: o.optJSONObject("shadowban"))?.let(::parseShadowban),
            views = o0.optInt("page_views", o.optInt("page_views")),
            favoritedBy = o0.optInt("favorited_by", o.optInt("favorited_by")),
            favorited = o0.bool("favorited") ?: o.bool("favorited"),
        )
    }

    /** `banner` is `{url, original}` (url = FSB's cached proxy) or null when the account has none. */
    private fun banner(o: JSONObject): String? {
        if (!o.has("banner") || o.isNull("banner")) return null
        o.optJSONObject("banner")?.let { b -> return Net.abs(b.str("url") ?: b.str("original")) }
        return Net.abs(o.str("banner"))
    }

    private fun parseShadowban(s: JSONObject): Shadowban {
        val t = s.optJSONObject("tests") ?: JSONObject()
        val search = t.bool("search_ban")
        val sugg = t.bool("search_suggestion_ban") ?: t.bool("suggestion_ban")
        val ghost = t.bool("ghost_ban")
        val reply = t.bool("reply_deboosting")
        val sens = t.bool("sensitive_profile") ?: t.bool("possibly_sensitive")
        val restricted = search == true || sugg == true || ghost == true || reply == true
        val anyNull = listOf(search, sugg, ghost, reply, sens).any { it == null }
        val verdict = when (s.optString("verdict").lowercase()) {
            "clean" -> Verdict.CLEAN
            "sensitive" -> Verdict.SENSITIVE
            "shadowbanned", "restricted" -> Verdict.SHADOWBANNED
            "unknown" -> Verdict.UNKNOWN
            // Fall back to the individual tests; incomplete tests are never treated as clean.
            else -> when {
                restricted || s.optBoolean("is_shadowbanned") -> Verdict.SHADOWBANNED
                anyNull -> Verdict.UNKNOWN
                sens == true -> Verdict.SENSITIVE
                else -> Verdict.CLEAN
            }
        }
        return Shadowban(
            verdict = verdict,
            score = if (s.isNull("visibility_score") || !s.has("visibility_score")) null else s.optInt("visibility_score"),
            searchBan = search, suggestionBan = sugg, ghostBan = ghost, replyDeboost = reply, sensitive = sens,
            lastChecked = s.optLong("last_checked"),
        )
    }

    private fun unknownShadowban() = Shadowban(Verdict.UNKNOWN, null, null, null, null, null, null, 0)

    private fun twitterTime(s: String): Long = runCatching {
        java.text.SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy", java.util.Locale.ENGLISH).parse(s)!!.time / 1000
    }.getOrDefault(0L)

    private fun JSONObject.str(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }

    /** true / false / null (missing or JSON null). */
    private fun JSONObject.bool(key: String): Boolean? = if (!has(key) || isNull(key)) null else when (val v = opt(key)) {
        is Boolean -> v
        is Number -> v.toInt() != 0
        is String -> v.equals("true", true) || v == "1"
        else -> null
    }

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
    private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }

    fun profileUrl(handle: String) = "${Net.BASE}?view=target&target=${enc(handle)}"
}
