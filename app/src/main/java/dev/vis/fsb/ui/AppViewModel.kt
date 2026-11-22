package dev.vis.fsb.ui

import android.app.Application
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.vis.fsb.data.Account
import dev.vis.fsb.data.ApiException
import dev.vis.fsb.data.AuthException
import dev.vis.fsb.data.DeviceCode
import dev.vis.fsb.data.Downloader
import dev.vis.fsb.data.FsbApi
import dev.vis.fsb.data.Insights
import dev.vis.fsb.data.Media
import dev.vis.fsb.data.MediaRef
import dev.vis.fsb.data.Net
import dev.vis.fsb.data.NotFoundException
import dev.vis.fsb.data.Post
import dev.vis.fsb.data.Prefs
import dev.vis.fsb.data.Profile
import dev.vis.fsb.data.ProfileSummary
import dev.vis.fsb.data.RateLimitedException
import dev.vis.fsb.data.Release
import dev.vis.fsb.data.Updater
import dev.vis.fsb.data.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.IOException

enum class Tab { EXPLORE, SEARCH, FAVORITES, ACCOUNT }

sealed interface Route {
    data class ProfileRoute(val handle: String) : Route
    data class Viewer(val items: List<MediaRef>, val start: Int) : Route
    data object Login : Route
}

enum class BoardFilter(val api: String) { ALL("all"), CLEAN("clean"), RESTRICTED("shadowbanned"), SENSITIVE("sensitive"), UNKNOWN("unknown") }
enum class BoardSort(val api: String, val label: String) { RECENT("recent", "Recent"), VIEWS("views", "Most viewed"), FAVORITES("favorites", "Most favorited"), FOLLOWERS("followers", "Most followers") }

/** Like [runCatching] but never swallows coroutine cancellation. */
inline fun <T> attempt(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (c: CancellationException) {
    throw c
} catch (t: Throwable) {
    Result.failure(t)
}

@Stable
class ProfileState(val handle: String) {
    var loading by mutableStateOf(true)
    var refreshing by mutableStateOf(false)
    var profile by mutableStateOf<Profile?>(null)
    var posts by mutableStateOf<List<Post>>(emptyList())
    var postsLoading by mutableStateOf(true)
    var postsError by mutableStateOf<String?>(null)
    var insights by mutableStateOf<Insights?>(null)
    var error by mutableStateOf<String?>(null)
    var notFound by mutableStateOf(false)
    var loadedAt = 0L
    var busy = false
}

data class UpdateUi(
    val release: Release? = null,
    val checking: Boolean = false,
    /** Download progress 0..1 while an update is being fetched. */
    val progress: Float? = null,
    val message: String? = null,
    val dismissed: Boolean = false,
)

/** State of an in-progress device-flow sign-in. */
@Stable
class LoginState {
    var code by mutableStateOf<DeviceCode?>(null)
    var error by mutableStateOf<String?>(null)
    var expired by mutableStateOf(false)
    var finishing by mutableStateOf(false)
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    init { Net.init(app) }

    val prefs = Prefs(app)
    val settings: StateFlow<Settings> = prefs.settings
    val recent: StateFlow<List<String>> = prefs.recent

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages
    fun toast(msg: String) { _messages.tryEmit(msg) }

    // ------------------------------------------------------------- navigation

    var tab by mutableStateOf(Tab.EXPLORE)
        private set
    val stack = mutableStateListOf<Route>()

    fun selectTab(t: Tab) {
        if (stack.isNotEmpty()) {
            stack.clear()
            profiles.clear()
        }
        tab = t
        if (t == Tab.FAVORITES && signedIn) loadLists()
        if (t == Tab.ACCOUNT && signedIn) refreshAccount()
    }

    fun push(r: Route) {
        if (stack.lastOrNull() != r) stack.add(r)
    }

    fun back(): Boolean {
        if (stack.isEmpty()) return false
        val r = stack.removeAt(stack.lastIndex)
        if (r is Route.ProfileRoute && stack.none { it is Route.ProfileRoute && it.handle.equals(r.handle, true) }) {
            profiles.remove(r.handle.lowercase())
        }
        if (r is Route.Login) cancelLogin()
        return true
    }

    fun openProfile(handle: String) {
        val h = handle.trim().removePrefix("@")
        if (!handleRx.matches(h)) return
        prefs.addRecent(h)
        push(Route.ProfileRoute(h))
        loadProfile(h)
    }

    fun openViewer(items: List<MediaRef>, start: Int) { if (items.isNotEmpty()) push(Route.Viewer(items, start)) }

    // ------------------------------------------------------------- account (token auth)

    var account by mutableStateOf<Account?>(null)
        private set
    var signedIn by mutableStateOf(Net.tokens.token != null)
        private set
    var accountLoading by mutableStateOf(false)
        private set

    fun refreshAccount() {
        if (!signedIn || accountLoading) return
        accountLoading = true
        viewModelScope.launch {
            attempt { FsbApi.me() }
                .onSuccess { account = it }
                .onFailure { if (!handleAuth(it) && account == null) toast(friendly(it)) }
            accountLoading = false
        }
    }

    /** A 401 means the token was revoked or expired: drop it and fall back to signed-out mode. */
    private fun handleAuth(t: Throwable): Boolean {
        if (t !is AuthException || !signedIn) return false
        clearLocalSession()
        toast("Your FSB session ended — please sign in again")
        return true
    }

    private fun clearLocalSession() {
        Net.tokens.clear()
        signedIn = false
        account = null
        favorites = emptyList()
        tracked = emptyList()
        favoriteSet.clear()
        trackedSet.clear()
    }

    fun signOut() = viewModelScope.launch {
        attempt { FsbApi.revokeCurrentToken() }
        clearLocalSession()
        toast("Signed out")
    }

    fun setHidePresence(hide: Boolean) {
        val a = account ?: return
        account = a.copy(hidePresence = hide)
        viewModelScope.launch {
            attempt { FsbApi.setHidePresence(hide) }.onFailure {
                account = account?.copy(hidePresence = !hide)
                if (!handleAuth(it)) toast(friendly(it))
            }
        }
    }

    // ------------------------------------------------------------- device-flow sign-in

    val login = LoginState()
    private var loginJob: Job? = null

    fun openLogin() {
        if (signedIn) { selectTab(Tab.ACCOUNT); return }
        push(Route.Login)
        if (loginJob?.isActive != true) startLogin()
    }

    fun startLogin() {
        loginJob?.cancel()
        login.code = null
        login.error = null
        login.expired = false
        login.finishing = false
        loginJob = viewModelScope.launch {
            val code = attempt { FsbApi.startDeviceFlow() }.getOrElse { login.error = friendly(it); return@launch }
            login.code = code
            var interval = code.interval * 1000L
            while (true) {
                delay(interval)
                if (System.currentTimeMillis() >= code.expiresAt) { login.expired = true; return@launch }
                val r = attempt { FsbApi.pollDeviceToken(code.deviceCode) }
                val token = r.getOrNull()
                val e = r.exceptionOrNull()
                when {
                    token != null -> { finishLogin(token); return@launch }
                    e is FsbApi.SlowDown -> interval += 5_000
                    e is RateLimitedException -> interval = maxOf(interval, e.retryAfter * 1000L)
                    e is IOException -> Unit // keep polling through flaky connectivity
                    e is ApiException -> {
                        when (e.code) {
                            "expired_token", "invalid_grant" -> login.expired = true
                            "access_denied" -> login.error = "Sign-in was declined on visiuun.com"
                            else -> login.error = friendly(e)
                        }
                        return@launch
                    }
                }
            }
        }
    }

    private suspend fun finishLogin(token: String) {
        login.finishing = true
        Net.tokens.save(token)
        signedIn = true
        attempt { FsbApi.me() }.onSuccess { account = it }
        loadLists()
        refreshBoard()
        profiles.values.forEach { it.loadedAt = 0 }
        toast("Signed in" + (account?.name?.let { " as $it" } ?: ""))
        if (stack.lastOrNull() == Route.Login) stack.removeAt(stack.lastIndex)
        login.finishing = false
        login.code = null
    }

    private fun cancelLogin() {
        loginJob?.cancel()
        loginJob = null
        login.code = null
    }

    // ------------------------------------------------------------- explore board

    val board = mutableStateListOf<ProfileSummary>()
    var boardLoading by mutableStateOf(false)
        private set
    var boardRefreshing by mutableStateOf(false)
        private set
    var boardError by mutableStateOf<String?>(null)
        private set
    var boardHasMore by mutableStateOf(true)
        private set
    var boardTotal by mutableStateOf(0)
        private set
    var boardFilter by mutableStateOf(BoardFilter.ALL)
        private set
    var boardSort by mutableStateOf(BoardSort.RECENT)
        private set
    var randomLoading by mutableStateOf(false)
        private set
    private var boardPage = 1
    private var boardJob: Job? = null

    fun chooseBoardFilter(f: BoardFilter) { if (f != boardFilter) { boardFilter = f; refreshBoard(pull = false) } }
    fun chooseBoardSort(s: BoardSort) { if (s != boardSort) { boardSort = s; refreshBoard(pull = false) } }

    fun loadMoreBoard() {
        if (boardLoading || !boardHasMore) return
        boardLoading = true
        boardJob = viewModelScope.launch {
            try {
                val page = boardPage
                var tries = 0
                var res = attempt { FsbApi.explore(page, boardFilter.api, boardSort.api) }
                // Retry transient network failures (e.g. connectivity still coming up at app start).
                while (res.exceptionOrNull() is IOException && tries++ < 3) {
                    delay(1500L * tries)
                    res = attempt { FsbApi.explore(page, boardFilter.api, boardSort.api) }
                }
                res.onSuccess { p ->
                    val known = board.map { it.username.lowercase() }.toHashSet()
                    board.addAll(p.items.filter { known.add(it.username.lowercase()) })
                    p.items.forEach { if (it.favorited == true) favoriteSet.add(it.username) }
                    boardHasMore = p.hasMore
                    boardTotal = p.total
                    boardPage = page + 1
                    boardError = null
                }.onFailure {
                    if (handleAuth(it)) return@onFailure
                    boardError = friendly(it)
                }
            } finally {
                boardLoading = false
                boardRefreshing = false
            }
        }
    }

    fun loadMoreBoardRetry() {
        boardError = null
        loadMoreBoard()
    }

    fun refreshBoard(pull: Boolean = true) {
        boardJob?.cancel()
        boardRefreshing = pull
        boardLoading = false
        boardPage = 1
        boardHasMore = true
        boardError = null
        board.clear()
        loadMoreBoard()
    }

    fun randomProfile() {
        if (randomLoading) return
        randomLoading = true
        viewModelScope.launch {
            attempt { FsbApi.random(boardFilter.api) }
                .onSuccess { h -> if (h != null) openProfile(h) else toast("No random profile available right now") }
                .onFailure { toast(friendly(it)) }
            randomLoading = false
        }
    }

    // ------------------------------------------------------------- search

    var query by mutableStateOf("")
        private set
    var results by mutableStateOf<List<ProfileSummary>>(emptyList())
        private set
    var searching by mutableStateOf(false)
        private set
    var searchError by mutableStateOf<String?>(null)
        private set
    private var searchJob: Job? = null
    private val searchCache = HashMap<String, List<ProfileSummary>>()

    fun onQuery(q: String) {
        query = q
        searchJob?.cancel()
        val clean = cleanQuery(q)
        if (clean.length < 2) {
            results = emptyList(); searching = false; searchError = null
            return
        }
        searchCache[clean.lowercase()]?.let { results = it; searching = false; searchError = null; return }
        searching = true
        searchJob = viewModelScope.launch {
            delay(450) // uncached searches are limited to 10/min, so wait for typing to settle
            attempt { FsbApi.search(clean) }
                .onSuccess { results = it; searchCache[clean.lowercase()] = it; searchError = null }
                .onFailure { searchError = friendly(it) }
            searching = false
        }
    }

    /** Turns "@name", "x.com/name/status/1" or a profile link into the bare query the API expects. */
    fun cleanQuery(q: String): String {
        val t = q.trim()
        Regex("""(?:x|twitter)\.com/([A-Za-z0-9_]{1,15})""").find(t)?.let { return it.groupValues[1] }
        Regex("""[?&]target=([A-Za-z0-9_]{1,15})""").find(t)?.let { return it.groupValues[1] }
        return t.removePrefix("@")
    }

    fun removeRecent(h: String) = prefs.removeRecent(h)
    fun clearRecent() = prefs.clearRecent()

    // ------------------------------------------------------------- favorites & tracking

    var favorites by mutableStateOf<List<ProfileSummary>>(emptyList())
        private set
    var tracked by mutableStateOf<List<ProfileSummary>>(emptyList())
        private set
    var listsLoading by mutableStateOf(false)
        private set
    val favoriteSet = HandleSet(mutableStateMapOf())
    val trackedSet = HandleSet(mutableStateMapOf())

    class HandleSet(private val m: MutableMap<String, Boolean>) {
        operator fun contains(h: String) = m[h.lowercase()] == true
        fun add(h: String) { m[h.lowercase()] = true }
        fun remove(h: String) { m.remove(h.lowercase()) }
        fun set(h: String, on: Boolean) = if (on) add(h) else remove(h)
        fun reset(handles: List<String>) { m.clear(); handles.forEach(::add) }
        fun clear() = m.clear()
    }

    fun loadLists() {
        if (!signedIn || listsLoading) return
        listsLoading = true
        viewModelScope.launch {
            attempt { FsbApi.favorites() }
                .onSuccess { favorites = it; favoriteSet.reset(it.map { p -> p.username }) }
                .onFailure { if (!handleAuth(it)) toast(friendly(it)) }
            if (signedIn) attempt { FsbApi.tracked() }
                .onSuccess { tracked = it; trackedSet.reset(it.map { p -> p.username }) }
                .onFailure { if (!handleAuth(it)) toast(friendly(it)) }
            listsLoading = false
        }
    }

    private fun requireSignIn(what: String): Boolean {
        if (signedIn) return true
        toast("Sign in to $what")
        openLogin()
        return false
    }

    fun toggleFavorite(handle: String) {
        if (!requireSignIn("favorite profiles")) return
        val on = handle !in favoriteSet
        favoriteSet.set(handle, on)
        viewModelScope.launch {
            attempt { FsbApi.setFavorite(handle, on) }
                .onSuccess {
                    toast(if (on) "Added @$handle to favorites" else "Removed @$handle from favorites")
                    if (!on) favorites = favorites.filterNot { it.username.equals(handle, true) }
                    loadLists()
                }
                .onFailure {
                    favoriteSet.set(handle, !on)
                    if (handleAuth(it)) return@onFailure
                    toast(
                        when ((it as? ApiException)?.code) {
                            "limit_reached" -> "You can favorite up to 6 profiles — remove one first"
                            "insufficient_scope" -> "This sign-in can't change favorites — sign out and in again"
                            else -> friendly(it)
                        },
                    )
                }
        }
    }

    fun toggleTracked(handle: String) {
        if (!requireSignIn("track accounts")) return
        val on = handle !in trackedSet
        trackedSet.set(handle, on)
        viewModelScope.launch {
            attempt { FsbApi.setTracked(handle, on) }
                .onSuccess {
                    toast(if (on) "Tracking @$handle — you'll get email alerts" else "Stopped tracking @$handle")
                    if (!on) tracked = tracked.filterNot { it.username.equals(handle, true) }
                    loadLists()
                }
                .onFailure {
                    trackedSet.set(handle, !on)
                    if (handleAuth(it)) return@onFailure
                    val e = it as? ApiException
                    toast(
                        when {
                            e?.code == "limit_reached" -> e.message?.takeIf(String::isNotBlank) ?: "You can track up to 5 accounts plus your own"
                            e?.code == "invalid_value" && e.message.orEmpty().contains("email", true) ->
                                "Add an email to your FSB account on visiuun.com to get alerts"
                            else -> friendly(it)
                        },
                    )
                }
        }
    }

    // ------------------------------------------------------------- profiles

    val profiles = mutableStateMapOf<String, ProfileState>()

    fun profileState(handle: String): ProfileState = profiles.getOrPut(handle.lowercase()) { ProfileState(handle) }

    fun loadProfile(handle: String, force: Boolean = false) {
        val st = profileState(handle)
        if (!force && st.profile != null && System.currentTimeMillis() - st.loadedAt < 5 * 60_000) return
        if (st.busy) return
        st.busy = true
        if (st.profile != null) st.refreshing = true else st.loading = true
        st.error = null
        viewModelScope.launch {
            val r = attempt { FsbApi.profile(handle) }
            r.onSuccess { p ->
                st.profile = p
                st.notFound = false
                st.loadedAt = System.currentTimeMillis()
                p.favorited?.let { favoriteSet.set(p.screenName, it) }
            }.onFailure {
                when {
                    handleAuth(it) -> st.error = friendly(it)
                    it is NotFoundException -> st.notFound = true
                    (it as? ApiException)?.code == "invalid_username" -> st.notFound = true
                    st.profile == null -> st.error = friendly(it)
                    else -> toast(friendly(it))
                }
            }
            st.loading = false

            if (r.isSuccess) {
                attempt { FsbApi.posts(handle, st.profile) }
                    .onSuccess { st.posts = it; st.postsError = null; st.insights = FsbApi.insights(it) }
                    .onFailure { if (st.posts.isEmpty()) st.postsError = friendly(it) else toast(friendly(it)) }
            }
            st.postsLoading = false
            st.refreshing = false
            st.busy = false
        }
    }

    fun retryProfile(handle: String) {
        val st = profileState(handle)
        st.profile = null
        st.notFound = false
        st.postsLoading = true
        loadProfile(handle, force = true)
    }

    // ------------------------------------------------------------- downloads

    fun download(items: List<Pair<String, Media>>) {
        if (items.isEmpty()) { toast("Nothing to download"); return }
        toast(if (items.size == 1) "Downloading…" else "Downloading ${items.size} files…")
        viewModelScope.launch {
            val r = Downloader.download(getApplication(), items)
            prefs.downloadCount = prefs.downloadCount + r.saved
            toast(
                when {
                    r.saved == 0 && r.skipped == 0 -> "Download failed"
                    r.saved + r.skipped == 1 && r.failed == 0 ->
                        (if (r.saved == 1) "Saved " else "Already saved: ") + r.lastName
                    else -> buildString {
                        append("Saved ${r.saved} to Downloads/${Downloader.FOLDER}")
                        if (r.skipped > 0) append(" · ${r.skipped} already saved")
                        if (r.failed > 0) append(" · ${r.failed} failed")
                    }
                },
            )
        }
    }

    fun updateSettings(t: (Settings) -> Settings) = prefs.updateSettings(t)

    // ------------------------------------------------------------- app updates (GitHub Releases)

    var update by mutableStateOf(UpdateUi())
        private set

    fun checkForUpdate(manual: Boolean = true) {
        if (update.checking || update.progress != null) return
        viewModelScope.launch {
            update = update.copy(checking = true, message = null, dismissed = false)
            attempt { Updater.check() }
                .onSuccess { r ->
                    prefs.lastUpdateCheck = System.currentTimeMillis()
                    update = update.copy(
                        checking = false, release = r,
                        message = if (r == null) "You're on the latest version (${Updater.currentVersion})" else "Version ${r.version} is available",
                    )
                }
                .onFailure { update = update.copy(checking = false, message = if (manual) "Couldn't check for updates" else null) }
        }
    }

    fun installUpdate() {
        val r = update.release ?: return
        val app = getApplication<Application>()
        if (!Updater.canInstall(app)) {
            update = update.copy(message = "Allow installing apps from FSB, then tap Update again")
            Updater.openInstallPermissionSettings(app)
            return
        }
        viewModelScope.launch {
            update = update.copy(progress = 0f, message = null)
            attempt {
                val apk = Updater.download(app, r) { p -> update = update.copy(progress = p) }
                Updater.install(app, apk)
            }.onSuccess { update = update.copy(progress = null, message = "Installing ${r.version}…") }
                .onFailure { update = update.copy(progress = null, message = "Update failed: ${it.message ?: "unknown error"}") }
        }
    }

    fun dismissUpdate() { update = update.copy(dismissed = true) }

    fun friendly(t: Throwable): String = when (t) {
        is RateLimitedException -> if (signedIn) t.message!! else "${t.message} · signing in raises the limit"
        is AuthException -> "Please sign in again"
        is ApiException -> when (t.code) {
            "upstream_unavailable" -> "X isn't responding right now — try again shortly"
            else -> t.message ?: "Something went wrong"
        }
        is IOException -> "No connection to visiuun.com — check your network"
        else -> t.message ?: "Something went wrong"
    }

    init {
        if (signedIn) {
            refreshAccount()
            loadLists()
        }
        loadMoreBoard()
        if (settings.value.autoUpdate && System.currentTimeMillis() - prefs.lastUpdateCheck > 6 * 3600_000L) {
            checkForUpdate(manual = false)
        }
    }

    companion object {
        val handleRx = Regex("^[A-Za-z0-9_]{1,15}$")
    }
}
