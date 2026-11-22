@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vis.fsb.ui

import android.content.Intent
import android.view.ViewGroup
import androidx.annotation.OptIn as AndroidOptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.SubcomposeAsyncImage
import dev.vis.fsb.data.Downloader
import dev.vis.fsb.data.Media
import dev.vis.fsb.data.MediaRef

@Composable
fun MediaViewer(vm: AppViewModel, items: List<MediaRef>, start: Int) {
    val pager = rememberPagerState(initialPage = start.coerceIn(0, (items.size - 1).coerceAtLeast(0))) { items.size }
    var chrome by remember { mutableStateOf(true) }
    val download = LocalDownload.current
    val uri = LocalUriHandler.current
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val current = items.getOrNull(pager.currentPage)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1, key = { "${items[it].post.id}_${items[it].index}" }) { page ->
            val ref = items[page]
            if (ref.media.isVideo) {
                VideoPage(ref.media, active = pager.currentPage == page, autoplay = true, onChrome = { chrome = it })
            } else {
                ZoomableImage(ref.media, highQuality = settings.highQuality, onTap = { chrome = !chrome })
            }
        }

        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.45f)).statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.back() }, colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)) {
                    Icon(Icons.Rounded.Close, "Close")
                }
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        current?.post?.authorName.orEmpty(), color = Color.White,
                        style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${pager.currentPage + 1} / ${items.size}" + (current?.let { " · ${shortDate(it.post.createdTs)}" } ?: ""),
                        color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        AnimatedVisibility(
            chrome && current != null,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.dp),
        ) {
            val ref = current ?: return@AnimatedVisibility
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (ref.post.text.isNotBlank()) {
                    Text(
                        ref.post.text, color = Color.White, style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                            .background(Color.Black.copy(alpha = 0.45f), MaterialTheme.shapes.large).padding(12.dp),
                    )
                }
                HorizontalFloatingToolbar(
                    expanded = true,
                    colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
                ) {
                    IconButton(onClick = { download(listOf(ref.post.authorHandle to ref.media)) }) { Icon(Icons.Rounded.Download, "Download") }
                    if (ref.post.media.size > 1) {
                        IconButton(onClick = { download(Downloader.forPost(ref.post)) }) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                Text("All", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    IconButton(onClick = { vm.openProfile(ref.post.authorHandle) }) { Icon(Icons.Rounded.Person, "Author profile") }
                    IconButton(onClick = {
                        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, ref.post.url)
                        context.startActivity(Intent.createChooser(i, "Share post"))
                    }) { Icon(Icons.Rounded.Share, "Share") }
                    IconButton(onClick = { uri.openUri(ref.post.url) }) { Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Open on X") }
                }
            }
        }
    }
}

@Composable
private fun ZoomableImage(media: Media, highQuality: Boolean, onTap: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        offset = if (scale <= 1.01f) Offset.Zero else offset + pan * scale
    }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1.2f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                    },
                )
            }
            .transformable(state, canPan = { scale > 1.01f }),
        contentAlignment = Alignment.Center,
    ) {
        SubcomposeAsyncImage(
            model = if (highQuality) Downloader.bestUrl(media) else media.url,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale
                translationX = offset.x; translationY = offset.y
            },
            loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(64.dp)) } },
        )
    }
}

@AndroidOptIn(UnstableApi::class)
@Composable
private fun VideoPage(media: Media, active: Boolean, autoplay: Boolean, onChrome: (Boolean) -> Unit) {
    val context = LocalContext.current
    val player = remember(media.url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(media.url))
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
        }
    }
    LaunchedEffect(active) { player.playWhenReady = active && autoplay; if (!active) player.pause() }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = {
            PlayerView(it).apply {
                this.player = player
                useController = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(android.graphics.Color.BLACK)
                setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v -> onChrome(v == android.view.View.VISIBLE) })
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}
