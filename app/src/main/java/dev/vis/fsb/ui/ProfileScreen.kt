@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package dev.vis.fsb.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.vis.fsb.data.Downloader
import dev.vis.fsb.data.FsbApi
import dev.vis.fsb.data.Insights
import dev.vis.fsb.data.MediaRef
import dev.vis.fsb.data.Post
import dev.vis.fsb.data.PostFilter
import dev.vis.fsb.data.PostLayout
import dev.vis.fsb.data.Profile
import dev.vis.fsb.data.Quote
import dev.vis.fsb.data.Settings
import dev.vis.fsb.ui.theme.StatusColors

@Composable
fun ProfileScreen(vm: AppViewModel, handle: String) {
    val st = vm.profileState(handle)
    val settings by vm.settings.collectAsStateWithLifecycle()
    val grid = rememberLazyGridState()
    val download = LocalDownload.current
    val context = LocalContext.current
    var filter by rememberSaveable { mutableStateOf(PostFilter.ALL) }
    val layout = settings.postLayout

    val posts = remember(st.posts, filter) {
        st.posts.filter {
            when (filter) {
                PostFilter.ALL -> true
                PostFilter.MEDIA -> it.media.isNotEmpty()
                PostFilter.VIDEOS -> it.media.any { m -> m.isVideo }
                PostFilter.NO_REPOSTS -> !it.isRepost
            }
        }
    }
    val gallery = remember(posts) { posts.flatMap { p -> p.media.indices.map { MediaRef(p, it) } } }
    val allMedia = remember(st.posts) { st.posts.flatMap { Downloader.forPost(it) }.distinctBy { it.second.url } }

    val scrolled by remember { derivedStateOf { grid.firstVisibleItemIndex > 0 || grid.firstVisibleItemScrollOffset > 300 } }
    val share = {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "Shadowban check for @${st.profile?.screenName ?: handle} on FSB: ${st.profile?.fsbUrl ?: FsbApi.profileUrl(handle)}")
        context.startActivity(Intent.createChooser(i, "Share profile"))
    }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = if (layout == PostLayout.GALLERY) GridCells.Adaptive(116.dp) else GridCells.Fixed(1),
            state = grid,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(if (layout == PostLayout.GALLERY) 3.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(if (layout == PostLayout.GALLERY) 3.dp else 0.dp),
        ) {
            val full: (androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope) -> GridItemSpan = { GridItemSpan(it.maxLineSpan) }
            when {
                st.loading && st.profile == null -> item(span = full) {
                    Box(Modifier.fillMaxWidth().height(520.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            LoadingIndicator(Modifier.size(80.dp))
                            Spacer(Modifier.height(16.dp))
                            Text("Running live checks on @$handle…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                st.notFound -> item(span = full) {
                    Box(Modifier.statusBarsPadding().padding(top = 56.dp)) {
                        EmptyState(Icons.Rounded.PersonOff, MaterialShapes.Ghostish, "Account unavailable", "@$handle doesn't exist, was deleted, or is suspended on X.")
                    }
                }
                st.profile == null -> item(span = full) {
                    Box(Modifier.statusBarsPadding().padding(top = 56.dp)) {
                        EmptyState(Icons.Rounded.CloudOff, MaterialShapes.Cookie9Sided, "Couldn't check @$handle", st.error) {
                            Button(onClick = { vm.retryProfile(handle) }) { Text("Try again") }
                        }
                    }
                }
                else -> {
                    val p = st.profile!!
                    item(span = full, key = "header") {
                        Header(
                            p, favorited = p.screenName in vm.favoriteSet,
                            onFavorite = { vm.toggleFavorite(p.screenName) },
                            tracked = p.screenName in vm.trackedSet,
                            onTrack = { vm.toggleTracked(p.screenName) },
                            onDownloadAll = { download(allMedia) },
                            mediaCount = allMedia.size,
                        )
                    }
                    item(span = full, key = "status") { Centered { StatusCard(p, st.refreshing) { vm.loadProfile(handle, force = true) } } }
                    item(span = full, key = "tests") { Centered { TestsCard(p) } }
                    if (p.history.size >= 2) item(span = full, key = "history") { Centered { HistoryCard(p) } }
                    item(span = full, key = "insights") { Centered { InsightsCard(st.insights, st.postsLoading) } }
                    item(span = full, key = "posts_header") {
                        Centered {
                            PostsHeader(
                                count = posts.size, total = st.posts.size, filter = filter, onFilter = { filter = it },
                                layout = layout, onLayout = { l -> vm.updateSettings { it.copy(postLayout = l) } },
                                onDownload = { download(posts.flatMap { Downloader.forPost(it) }) },
                                mediaInView = posts.sumOf { it.media.size },
                            )
                        }
                    }
                    when {
                        st.postsLoading && st.posts.isEmpty() -> item(span = full) {
                            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                        }
                        st.postsError != null && st.posts.isEmpty() -> item(span = full) {
                            EmptyState(Icons.Rounded.CloudOff, MaterialShapes.Puffy, "Couldn't load posts", st.postsError)
                        }
                        posts.isEmpty() -> item(span = full) {
                            EmptyState(Icons.Rounded.GridView, MaterialShapes.Clover8Leaf, "No posts", "Nothing matches this filter.")
                        }
                        layout == PostLayout.GALLERY -> itemsIndexed(gallery, key = { _, r -> "g_${r.post.id}_${r.index}" }) { i, r ->
                            GalleryThumb(r, settings, onOpen = { vm.openViewer(gallery, i) }, onLongPress = { download(listOf(r.post.authorHandle to r.media)) })
                        }
                        else -> itemsIndexed(posts, key = { _, p2 -> "p_" + p2.id }, span = { _, _ -> GridItemSpan(maxLineSpan) }) { _, post ->
                            Centered {
                                PostCard(
                                    post, settings,
                                    onMedia = { idx -> vm.openViewer(post.media.indices.map { MediaRef(post, it) }, idx) },
                                    onDownload = { download(Downloader.forPost(post)) },
                                    onAuthor = { if (!post.authorHandle.equals(handle, true)) vm.openProfile(post.authorHandle) },
                                    onQuoteMedia = { q, idx -> val qp = quotePost(q, post); vm.openViewer(qp.media.indices.map { MediaRef(qp, it) }, idx) },
                                    onQuoteAuthor = { q -> vm.openProfile(q.username) },
                                )
                            }
                        }
                    }
                    item(span = full, key = "credit") {
                        Text(
                            "Shadowban data by FSB Sentinel · visiuun.com/fsb",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        )
                    }
                }
            }
        }

        TopBar(
            title = st.profile?.name ?: "@$handle",
            solid = scrolled || st.profile == null,
            onBack = { vm.back() },
            onRefresh = { vm.loadProfile(handle, force = true) },
            onShare = share,
            handle = st.profile?.screenName ?: handle,
            fsbUrl = st.profile?.fsbUrl ?: FsbApi.profileUrl(handle),
            refreshing = st.refreshing,
            onDownloadAll = { download(allMedia) },
            tracked = (st.profile?.screenName ?: handle) in vm.trackedSet,
            onTrack = { vm.toggleTracked(st.profile?.screenName ?: handle) },
        )
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 760.dp).fillMaxWidth()) { content() }
    }
}

@Composable
private fun TopBar(
    title: String,
    solid: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onShare: () -> Unit,
    handle: String,
    fsbUrl: String,
    refreshing: Boolean,
    onDownloadAll: () -> Unit,
    tracked: Boolean,
    onTrack: () -> Unit,
) {
    val bg by animateColorAsState(if (solid) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent, label = "bar")
    val uri = LocalUriHandler.current
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().background(bg).statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", solid, onBack)
        AnimatedVisibility(solid, Modifier.weight(1f), enter = fadeIn(), exit = fadeOut()) {
            Text(title, style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
        }
        if (!solid) Spacer(Modifier.weight(1f))
        if (refreshing) LoadingIndicator(Modifier.size(40.dp)) else BarButton(Icons.Rounded.Refresh, "Refresh", solid, onRefresh)
        BarButton(Icons.Rounded.Share, "Share", solid, onShare)
        Box {
            BarButton(Icons.Rounded.MoreVert, "More", solid) { menu = true }
            DropdownMenu(menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(20.dp)) {
                DropdownMenuItem(
                    text = { Text("Open on X") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
                    onClick = { menu = false; uri.openUri("https://x.com/$handle") },
                )
                DropdownMenuItem(
                    text = { Text("Open on visiuun.com") }, leadingIcon = { Icon(Icons.Rounded.Link, null) },
                    onClick = { menu = false; uri.openUri(fsbUrl) },
                )
                DropdownMenuItem(
                    text = { Text(if (tracked) "Stop email alerts" else "Email alerts on changes") },
                    leadingIcon = { Icon(if (tracked) Icons.Rounded.NotificationsOff else Icons.Rounded.NotificationsActive, null) },
                    onClick = { menu = false; onTrack() },
                )
                DropdownMenuItem(
                    text = { Text("Status badge") }, leadingIcon = { Icon(Icons.Rounded.Shield, null) },
                    onClick = { menu = false; uri.openUri("https://visiuun.com/fsb/badge/$handle.svg") },
                )
                DropdownMenuItem(
                    text = { Text("Download all media") }, leadingIcon = { Icon(Icons.Rounded.Download, null) },
                    onClick = { menu = false; onDownloadAll() },
                )
            }
        }
    }
}

@Composable
private fun BarButton(icon: ImageVector, desc: String, solid: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        colors = if (solid) IconButtonDefaults.iconButtonColors()
        else IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.45f), contentColor = Color.White),
    ) { Icon(icon, desc) }
}

@Composable
private fun Header(
    p: Profile, favorited: Boolean, onFavorite: () -> Unit, tracked: Boolean, onTrack: () -> Unit,
    onDownloadAll: () -> Unit, mediaCount: Int,
) {
    val accent = levelColor(levelOf(p.shadowban))
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().aspectRatio(3.6f).background(Brush.linearGradient(listOf(accent.copy(alpha = 0.7f), cs.primaryContainer.copy(alpha = 0.35f))))) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent, Color.Transparent))))
        }
        Centered {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Avatar overlaps the banner by 44dp; report the reduced height so nothing below needs offsets.
                    Box(
                        Modifier
                            .layout { m, c ->
                                val pl = m.measure(c)
                                val lift = 44.dp.roundToPx()
                                layout(pl.width, pl.height - lift) { pl.place(0, -lift) }
                            }
                            .size(104.dp).clip(MaterialShapes.Cookie12Sided.toShape()).background(cs.background).padding(4.dp),
                    ) { Avatar(p.avatar, size = 96.dp, shape = MaterialShapes.Cookie12Sided.toShape(), fallback = dev.vis.fsb.data.Net.avatarProxy(p.screenName)) }
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledIconToggleButton(
                            checked = favorited,
                            onCheckedChange = { onFavorite() },
                            shapes = IconButtonDefaults.toggleableShapes(),
                            colors = IconButtonDefaults.filledIconToggleButtonColors(
                                containerColor = cs.surfaceContainerHighest,
                                contentColor = cs.onSurfaceVariant,
                                checkedContainerColor = Color(0xFFFF4D7A),
                                checkedContentColor = Color.White,
                            ),
                        ) { Icon(if (favorited) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favorite") }
                        FilledIconToggleButton(
                            checked = tracked,
                            onCheckedChange = { onTrack() },
                            shapes = IconButtonDefaults.toggleableShapes(),
                            colors = IconButtonDefaults.filledIconToggleButtonColors(
                                containerColor = cs.surfaceContainerHighest,
                                contentColor = cs.onSurfaceVariant,
                            ),
                        ) { Icon(if (tracked) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsNone, if (tracked) "Stop email alerts" else "Email alerts") }
                        FilledTonalButton(onClick = onDownloadAll, enabled = mediaCount > 0) {
                            Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (mediaCount > 0) "Save $mediaCount" else "No media", maxLines = 1, softWrap = false)
                        }
                    }
                }
                Column(Modifier.padding(top = 12.dp)) {
                    NameLine(p.name, p.verified, MaterialTheme.typography.headlineMediumEmphasized, maxLines = 3)
                    Text("@${p.screenName}", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (p.bio.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(p.bio, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (p.location.isNotBlank()) Meta(Icons.Rounded.LocationOn, p.location)
                        joinedDate(p.createdAt).takeIf { it.isNotBlank() }?.let { Meta(Icons.Rounded.CalendarMonth, "Joined $it") }
                        if (p.pageViews > 0) Meta(Icons.Rounded.Visibility, "${compact(p.pageViews.toLong())} views")
                        if (p.favoritedBy > 0) Meta(Icons.Rounded.Favorite, "${p.favoritedBy} favorited")
                        if (p.trackedBy > 0) Meta(Icons.Rounded.NotificationsActive, "${p.trackedBy} tracking")
                    }
                    Spacer(Modifier.height(16.dp))
                    Surface(shape = RoundedCornerShape(24.dp), color = cs.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(vertical = 14.dp)) {
                            StatBlock(compact(p.followers), "Followers", Modifier.weight(1f))
                            StatBlock(compact(p.following), "Following", Modifier.weight(1f))
                            StatBlock(compact(p.posts), "Posts", Modifier.weight(1f))
                            StatBlock(compact(p.likes), "Likes", Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Meta(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatusCard(p: Profile, refreshing: Boolean, onRecheck: () -> Unit) {
    val sb = p.shadowban
    val level = levelOf(sb)
    val c = levelColor(level)
    val shapes = remember(level) {
        when (level) {
            Level.OK -> listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Sunny, MaterialShapes.Cookie12Sided)
            Level.WARN -> listOf(MaterialShapes.Pentagon, MaterialShapes.Cookie6Sided, MaterialShapes.Gem)
            Level.BAD -> listOf(MaterialShapes.SoftBurst, MaterialShapes.Burst, MaterialShapes.Cookie4Sided)
            Level.UNKNOWN -> listOf(MaterialShapes.Ghostish, MaterialShapes.Puffy, MaterialShapes.Cookie7Sided)
        }
    }
    Surface(shape = RoundedCornerShape(32.dp), color = c.copy(alpha = 0.13f), modifier = Modifier.fillMaxWidth().padding(top = 0.dp)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Reach status", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Shrinks to fit instead of breaking words ("Sensitiv/e").
                val labelStyle = MaterialTheme.typography.displaySmallEmphasized
                BasicText(
                    sb.label,
                    style = labelStyle.copy(color = c, fontWeight = FontWeight.Black),
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = labelStyle.fontSize, stepSize = 1.sp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                val active = buildList {
                    if (sb.searchBan == true) add("Search ban")
                    if (sb.suggestionBan == true) add("Suggestion ban")
                    if (sb.ghostBan == true) add("Ghost ban")
                    if (sb.replyDeboost == true) add("Reply deboosting")
                    if (sb.sensitive == true) add("Sensitive profile")
                }
                Text(
                    when {
                        active.isNotEmpty() -> active.joinToString(" · ")
                        level == Level.UNKNOWN -> "Some tests couldn't be completed"
                        else -> "No active restrictions"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    buildString {
                        if (sb.lastChecked > 0) append("Checked ").append(ago(sb.lastChecked)) else append("Not checked yet")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(onClick = onRecheck, enabled = !refreshing) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (refreshing) "Refreshing…" else "Refresh")
                }
            }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                MorphingBlob(shapes, c.copy(alpha = 0.16f), Modifier.matchParentSize())
                CircularWavyProgressIndicator(
                    progress = { if (sb.score == null && p.history.isEmpty()) 0f else p.healthScore / 100f },
                    modifier = Modifier.size(104.dp),
                    color = c,
                    trackColor = c.copy(alpha = 0.2f),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (sb.score == null && p.history.isEmpty()) "?" else "${p.healthScore}", style = MaterialTheme.typography.headlineLargeEmphasized, fontWeight = FontWeight.Black)
                    Text("visibility", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TestsCard(p: Profile) {
    val sb = p.shadowban
    val tests = listOf(
        Triple("Search ban", "Posts hidden from search results, even for from: queries", sb.searchBan),
        Triple("Search suggestion ban", "Handle missing from typeahead suggestions", sb.suggestionBan),
        Triple("Ghost ban", "Replies hidden from everyone else in threads", sb.ghostBan),
        Triple("Reply deboosting", "Replies pushed behind \"Show more replies\"", sb.replyDeboost),
        Triple("Sensitive media flag", "Profile or media marked as potentially sensitive", sb.sensitive),
    )
    Column(Modifier.padding(top = 16.dp)) {
        SectionTitle("Live tests")
        tests.forEachIndexed { i, (name, desc, hit) ->
            val sensitiveRow = i == tests.lastIndex
            val color = when (hit) {
                null -> StatusColors.unknown
                false -> StatusColors.clean
                true -> if (sensitiveRow) StatusColors.warn else StatusColors.bad
            }
            Surface(shape = segmentShape(i, tests.size), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    ShapeBadge(
                        when (hit) { null -> Icons.Rounded.HelpOutline; false -> Icons.Rounded.CheckCircle; true -> if (sensitiveRow) Icons.Rounded.Warning else Icons.Rounded.Error },
                        when (hit) { null -> MaterialShapes.Puffy; false -> MaterialShapes.Cookie9Sided; true -> MaterialShapes.SoftBurst },
                        size = 40.dp, container = color.copy(alpha = 0.16f), content = color,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleMedium)
                        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(when (hit) { null -> "Unknown"; true -> "Detected"; false -> "Clear" }, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
private fun HistoryCard(p: Profile) {
    val pts = p.history.takeLast(60)
    val line = MaterialTheme.colorScheme.primary
    val fill = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    val grid = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.padding(top = 16.dp)) {
        SectionTitle("Health history")
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShapeBadge(Icons.Rounded.BarChart, MaterialShapes.Cookie7Sided, size = 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${pts.size} checks", style = MaterialTheme.typography.titleMedium)
                        Text("since ${shortDate(pts.first().time)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val lo = pts.minOf { it.score }
                    val hi = pts.maxOf { it.score }
                    Text(if (lo == hi) "$hi" else "$lo–$hi", style = MaterialTheme.typography.titleLargeEmphasized)
                }
                Spacer(Modifier.height(14.dp))
                Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                    val w = size.width
                    val h = size.height
                    listOf(0f, 0.5f, 1f).forEach { f ->
                        drawLine(grid, Offset(0f, h * f), Offset(w, h * f), strokeWidth = 1.dp.toPx())
                    }
                    val t0 = pts.first().time.toFloat()
                    val span = (pts.last().time - pts.first().time).coerceAtLeast(1).toFloat()
                    fun pos(i: Int) = Offset(((pts[i].time - t0) / span) * w, h - (pts[i].score / 100f) * h)
                    val path = Path().apply {
                        moveTo(pos(0).x, pos(0).y)
                        for (i in 1 until pts.size) {
                            // step chart: the score holds until the next check
                            lineTo(pos(i).x, pos(i - 1).y)
                            lineTo(pos(i).x, pos(i).y)
                        }
                    }
                    val area = Path().apply { addPath(path); lineTo(w, pos(pts.lastIndex).y); lineTo(w, h); lineTo(0f, h); close() }
                    drawPath(area, fill)
                    drawPath(path, line, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    pts.indices.filter { i -> i == pts.lastIndex || pts[i].score != pts.getOrNull(i - 1)?.score }.forEach { i ->
                        val c = when {
                            pts[i].score >= 90 -> StatusColors.clean
                            pts[i].score >= 60 -> StatusColors.warn
                            else -> StatusColors.bad
                        }
                        drawCircle(c, 5.dp.toPx(), pos(i))
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightsCard(i: Insights?, loading: Boolean) {
    val uri = LocalUriHandler.current
    Column(Modifier.padding(top = 16.dp)) {
        SectionTitle("Timeline insights")
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            when {
                i == null && loading -> Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                i == null -> Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    ShapeBadge(Icons.Rounded.Insights, MaterialShapes.Cookie6Sided, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Not enough original posts to compute insights.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ShapeBadge(Icons.Rounded.Insights, MaterialShapes.Cookie6Sided, size = 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Based on ${i.sampleSize} recent posts", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row {
                        StatBlock(compact(i.avgLikes), "Avg likes", Modifier.weight(1f))
                        StatBlock(compact(i.avgRetweets), "Avg reposts", Modifier.weight(1f))
                        StatBlock(compact(i.avgReplies), "Avg replies", Modifier.weight(1f))
                        StatBlock(compact(i.avgViews), "Avg views", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(16.dp))
                    Meter("Engagement rate", i.engagementRate, "%", (i.engagementRate / 100.0).coerceIn(0.0, 1.0).toFloat(), StatusColors.clean)
                    Meter("Link penalty exposure", i.linkPenalty, "%", (i.linkPenalty / 100.0).toFloat(), StatusColors.warn)
                    Meter("Media richness", i.mediaRichness, "%", (i.mediaRichness / 100.0).toFloat(), MaterialTheme.colorScheme.primary)
                    if (i.outliers.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        i.outliers.distinctBy { it.label }.forEachIndexed { idx, o ->
                            Surface(
                                onClick = { if (o.url.isNotBlank()) uri.openUri(o.url) },
                                shape = segmentShape(idx, i.outliers.size, big = 20.dp, small = 6.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(o.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                    Text(o.text.ifBlank { "(media only)" }, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "♥ ${compact(o.likes)}   ⟲ ${compact(o.retweets)}   💬 ${compact(o.replies)}   👁 ${compact(o.views)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Meter(label: String, value: Double, unit: String, fraction: Float, color: Color) {
    Column(Modifier.padding(bottom = 12.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("${compact(value)}$unit", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        androidx.compose.material3.LinearWavyProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = color,
            trackColor = color.copy(alpha = 0.18f),
        )
    }
}

@Composable
private fun PostsHeader(
    count: Int,
    total: Int,
    filter: PostFilter,
    onFilter: (PostFilter) -> Unit,
    layout: PostLayout,
    onLayout: (PostLayout) -> Unit,
    onDownload: () -> Unit,
    mediaInView: Int,
) {
    Column(Modifier.padding(top = 20.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Posts", style = MaterialTheme.typography.headlineSmallEmphasized)
                Text(
                    if (count == total) "$total recent posts" else "$count of $total posts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ConnectedChoice(
                listOf(PostLayout.FEED, PostLayout.GALLERY), layout,
                label = { if (it == PostLayout.FEED) "Feed" else "Gallery" },
                onSelect = onLayout,
                modifier = Modifier.width(220.dp),
                icon = { if (it == PostLayout.FEED) Icons.Rounded.ViewAgenda else Icons.Rounded.GridView },
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PostFilter.entries.forEach { f ->
                val sel = f == filter
                FilterChip(
                    selected = sel,
                    onClick = { onFilter(f) },
                    label = { Text(when (f) { PostFilter.ALL -> "All"; PostFilter.MEDIA -> "Media"; PostFilter.VIDEOS -> "Videos"; PostFilter.NO_REPOSTS -> "No reposts" }) },
                    leadingIcon = if (sel) ({ Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }) else null,
                    shape = CircleShape,
                )
            }
            if (mediaInView > 0) {
                FilledTonalButton(onClick = onDownload) {
                    Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save $mediaInView shown")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LazyGridItemScope.GalleryThumb(r: MediaRef, settings: Settings, onOpen: () -> Unit, onLongPress: () -> Unit) {
    val blur = settings.blurSensitive && r.post.sensitive
    Box(
        Modifier.aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress),
    ) {
        AsyncImage(r.media.thumb, null, Modifier.fillMaxSize().then(if (blur) Modifier.blur(24.dp) else Modifier), contentScale = ContentScale.Crop)
        if (r.media.isVideo) {
            Icon(
                Icons.Rounded.PlayArrow, "Video",
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).padding(3.dp),
                tint = Color.White,
            )
        }
        if (blur) Icon(Icons.Rounded.VisibilityOff, null, Modifier.align(Alignment.Center), tint = Color.White)
    }
}

@Composable
fun PostCard(
    post: Post, settings: Settings, onMedia: (Int) -> Unit, onDownload: () -> Unit, onAuthor: () -> Unit,
    onQuoteMedia: (Quote, Int) -> Unit = { _, _ -> }, onQuoteAuthor: (Quote) -> Unit = {},
) {
    val uri = LocalUriHandler.current
    var revealed by rememberSaveable(post.id) { mutableStateOf(false) }
    val hide = settings.blurSensitive && post.sensitive && !revealed
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Column(Modifier.padding(16.dp)) {
            if (post.isRepost) {
                Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Repeat, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text("Repost", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(CircleShape).combinedClickableCompat(onAuthor)) {
                    Avatar(post.authorAvatar, size = 40.dp, shape = MaterialShapes.Cookie7Sided.toShape(), fallback = dev.vis.fsb.data.Net.avatarProxy(post.authorHandle))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    NameLine(post.authorName, post.verified, MaterialTheme.typography.titleSmall)
                    Text(
                        "@${post.authorHandle} · ${shortDate(post.createdTs)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    )
                }
                if (post.pinned) {
                    Icon(Icons.Rounded.PushPin, "Pinned", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            val body = post.text.let { if (post.isRepost) it.replace(Regex("^RT @\\w+:\\s*"), "") else it }
                .replace(Regex("""\s*https://t\.co/\w+$"""), "")
            if (body.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(body, style = MaterialTheme.typography.bodyLarge)
            }
            if (post.media.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                MediaGrid(post, hide, onReveal = { revealed = true }, onMedia = onMedia)
            }
            post.quote?.let { q ->
                Spacer(Modifier.height(12.dp))
                QuoteBlock(q, post, hide, onReveal = { revealed = true }, onMedia = { onQuoteMedia(q, it) }, onAuthor = { onQuoteAuthor(q) })
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Count(Icons.AutoMirrored.Rounded.Reply, post.replies)
                Count(Icons.Rounded.Repeat, post.retweets)
                Count(Icons.Rounded.Favorite, post.likes)
                Count(Icons.Rounded.BarChart, post.views)
                Spacer(Modifier.weight(1f))
                if (post.media.isNotEmpty() || post.quote?.media?.isNotEmpty() == true) {
                    IconButton(onClick = onDownload) { Icon(Icons.Rounded.Download, "Download media") }
                }
                IconButton(onClick = { uri.openUri(post.url) }) { Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Open on X") }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit) = this.then(Modifier.combinedClickable(onClick = onClick))

@Composable
private fun Count(icon: ImageVector, n: Long) {
    Row(Modifier.padding(end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(compact(n), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MediaGrid(post: Post, hide: Boolean, onReveal: () -> Unit, onMedia: (Int) -> Unit) {
    val m = post.media
    val shape = RoundedCornerShape(20.dp)
    Box(Modifier.fillMaxWidth().clip(shape)) {
        when (m.size) {
            1 -> Thumb(post, 0, Modifier.fillMaxWidth().aspectRatio(if (m[0].isVideo) 16f / 9f else 4f / 3.6f), hide, onMedia)
            2 -> Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Thumb(post, 0, Modifier.weight(1f).aspectRatio(0.8f), hide, onMedia)
                Thumb(post, 1, Modifier.weight(1f).aspectRatio(0.8f), hide, onMedia)
            }
            3 -> Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Thumb(post, 0, Modifier.weight(1f).aspectRatio(0.8f), hide, onMedia)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Thumb(post, 1, Modifier.fillMaxWidth().aspectRatio(1.6f), hide, onMedia)
                    Thumb(post, 2, Modifier.fillMaxWidth().aspectRatio(1.6f), hide, onMedia)
                }
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                for (row in 0..1) Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Thumb(post, row * 2, Modifier.weight(1f).aspectRatio(1.3f), hide, onMedia)
                    Thumb(post, row * 2 + 1, Modifier.weight(1f).aspectRatio(1.3f), hide, onMedia)
                }
            }
        }
        if (hide) {
            Surface(
                onClick = onReveal,
                modifier = Modifier.matchParentSize(),
                color = Color.Black.copy(alpha = 0.55f),
                contentColor = Color.White,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Rounded.VisibilityOff, null)
                    Spacer(Modifier.height(6.dp))
                    Text("Sensitive media · tap to show", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun Thumb(post: Post, i: Int, modifier: Modifier, hide: Boolean, onMedia: (Int) -> Unit) {
    val media = post.media.getOrNull(i) ?: return
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest).combinedClickableCompat { onMedia(i) }) {
        AsyncImage(media.thumb, null, Modifier.fillMaxSize().then(if (hide) Modifier.blur(28.dp) else Modifier), contentScale = ContentScale.Crop)
        if (media.isVideo) {
            Box(
                Modifier.align(Alignment.Center).size(56.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.PlayArrow, "Play", Modifier.size(32.dp), tint = Color.White) }
        }
        if (i == 3 && post.media.size > 4) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                Text("+${post.media.size - 4}", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            }
        }
    }
}

/** Wraps a quoted post as a [Post] so its media can use the viewer and downloader. */
fun quotePost(q: Quote, parent: Post) = parent.copy(
    id = "q_" + q.id, url = q.url, text = q.text, authorName = q.username, authorHandle = q.username,
    authorAvatar = null, verified = false, media = q.media, quote = null, isRepost = false, repostOf = null, pinned = false,
)

@Composable
private fun QuoteBlock(q: Quote, parent: Post, hide: Boolean, onReveal: () -> Unit, onMedia: (Int) -> Unit, onAuthor: () -> Unit) {
    val uri = LocalUriHandler.current
    Surface(
        onClick = { uri.openUri(q.url) },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.combinedClickableCompat(onAuthor)) {
                Avatar(dev.vis.fsb.data.Net.avatarProxy(q.username), size = 22.dp, shape = CircleShape)
                Spacer(Modifier.width(6.dp))
                Text("@${q.username}", style = MaterialTheme.typography.labelLarge)
            }
            if (q.text.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(q.text, style = MaterialTheme.typography.bodyMedium, maxLines = 6, overflow = TextOverflow.Ellipsis)
            }
            if (q.media.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                MediaGrid(quotePost(q, parent), hide, onReveal = onReveal, onMedia = onMedia)
            }
        }
    }
}
