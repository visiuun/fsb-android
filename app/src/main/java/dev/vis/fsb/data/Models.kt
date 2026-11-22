package dev.vis.fsb.data

import androidx.compose.runtime.Immutable

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class PostLayout { FEED, GALLERY }
enum class PostFilter { ALL, MEDIA, VIDEOS, NO_REPOSTS }

@Immutable
data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val oled: Boolean = false,
    val dynamicColor: Boolean = true,
    val blurSensitive: Boolean = false,
    val autoplayVideos: Boolean = false,
    val postLayout: PostLayout = PostLayout.FEED,
    val highQuality: Boolean = true,
    val autoUpdate: Boolean = true,
)

/** API verdicts. A test that couldn't complete is `null` and is never reported as clean. */
enum class Verdict { CLEAN, SENSITIVE, SHADOWBANNED, UNKNOWN }

/** `shadowban` object shared by profiles, explore, search, favorites and tracked lists. */
@Immutable
data class Shadowban(
    val verdict: Verdict,
    val score: Int?,
    val searchBan: Boolean?,
    val suggestionBan: Boolean?,
    val ghostBan: Boolean?,
    val replyDeboost: Boolean?,
    val sensitive: Boolean?,
    val lastChecked: Long,
) {
    val restricted get() = searchBan == true || suggestionBan == true || ghostBan == true || replyDeboost == true

    val label: String
        get() = when (verdict) {
            Verdict.CLEAN -> "Clean"
            Verdict.SENSITIVE -> "Sensitive"
            Verdict.SHADOWBANNED -> "Shadowbanned"
            Verdict.UNKNOWN -> "Unknown"
        }

    /** Short pill text, e.g. "Search ban" or "3 restrictions". */
    val summary: String
        get() {
            if (verdict != Verdict.SHADOWBANNED) return label
            val l = buildList {
                if (searchBan == true) add("Search")
                if (suggestionBan == true) add("Suggestion")
                if (ghostBan == true) add("Ghost")
                if (replyDeboost == true) add("Reply")
            }
            return when (l.size) {
                0 -> label
                1 -> "${l[0]} ban"
                else -> "${l.size} restrictions"
            }
        }
}

/** A profile row in Explore, Search, Favorites and Tracked. */
@Immutable
data class ProfileSummary(
    val username: String,
    val name: String,
    val avatar: String?,
    val banner: String?,
    val verified: Boolean,
    val followers: Long,
    val accent: String?,
    val shadowban: Shadowban?,
    val views: Int,
    val favoritedBy: Int,
    val favorited: Boolean?,
)

@Immutable
data class ExplorePage(val items: List<ProfileSummary>, val hasMore: Boolean, val total: Int)

@Immutable
data class HistoryPoint(val time: Long, val status: String, val score: Int, val restrictions: String)

@Immutable
data class Profile(
    val screenName: String,
    val name: String,
    val avatar: String?,
    val banner: String?,
    val bio: String,
    val location: String,
    val createdAt: String,
    val followers: Long,
    val following: Long,
    val posts: Long,
    val likes: Long,
    val verified: Boolean,
    val url: String,
    val fsbUrl: String,
    val shadowban: Shadowban,
    val history: List<HistoryPoint>,
    val trackedBy: Int,
    val favoritedBy: Int,
    val pageViews: Int,
    val favorited: Boolean?,
) {
    val healthScore: Int get() = shadowban.score ?: history.lastOrNull()?.score ?: 0
}

@Immutable
data class Media(val type: String, val url: String, val thumb: String) {
    val isVideo get() = type == "video" || type == "animated_gif" || type == "gif"
}

@Immutable
data class Quote(val id: String, val username: String, val text: String, val media: List<Media>) {
    val url get() = "https://x.com/$username/status/$id"
}

@Immutable
data class Post(
    val id: String,
    val url: String,
    val text: String,
    val createdTs: Long,
    val likes: Long,
    val retweets: Long,
    val replies: Long,
    val views: Long,
    val pinned: Boolean,
    val isRepost: Boolean,
    val repostOf: String?,
    val authorName: String,
    val authorHandle: String,
    val authorAvatar: String?,
    val verified: Boolean,
    val sensitive: Boolean,
    val media: List<Media>,
    val quote: Quote?,
)

@Immutable
data class Outlier(val label: String, val url: String, val text: String, val likes: Long, val retweets: Long, val replies: Long, val views: Long)

@Immutable
data class Insights(
    val sampleSize: Int,
    val avgLikes: Double,
    val avgRetweets: Double,
    val avgReplies: Double,
    val avgViews: Double,
    val engagementRate: Double,
    val linkPenalty: Double,
    val mediaRichness: Double,
    val outliers: List<Outlier>,
)

/** A media item paired with the post it belongs to (gallery + downloads). */
@Immutable
data class MediaRef(val post: Post, val index: Int) {
    val media get() = post.media[index]
}

/** The signed-in FSB account (`GET /me`). */
@Immutable
data class Account(
    val name: String,
    val avatar: String?,
    val email: String?,
    val xHandle: String?,
    val discordName: String?,
    val scopes: List<String>,
    val hidePresence: Boolean?,
    val xShadowban: Shadowban?,
)

@Immutable
data class DeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val verificationUriComplete: String,
    val expiresAt: Long,
    val interval: Int,
)

@Immutable
data class AppToken(val name: String, val kind: String, val scopes: List<String>, val lastUsed: Long, val current: Boolean)

/** Error returned by the API envelope (`{"ok":false,"error":{code,message}}`). */
open class ApiException(val code: String, msg: String, val http: Int = 0) : Exception(msg)
class AuthException(code: String, msg: String) : ApiException(code, msg, 401)
class NotFoundException(msg: String) : ApiException("not_found", msg, 404)
class RateLimitedException(val retryAfter: Int) : ApiException("rate_limited", "Rate limited — try again in ${retryAfter}s", 429)
