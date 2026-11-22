@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package dev.vis.fsb.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.vis.fsb.data.Net
import dev.vis.fsb.data.ProfileSummary

@Composable
fun ExploreScreen(vm: AppViewModel) {
    val grid = rememberLazyGridState()
    val nearEnd by remember {
        derivedStateOf {
            val last = grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= grid.layoutInfo.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearEnd, vm.board.size) { if (nearEnd && vm.boardError == null) vm.loadMoreBoard() }

    val pull = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = vm.boardRefreshing,
        onRefresh = { vm.refreshBoard() },
        state = pull,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pull, isRefreshing = vm.boardRefreshing,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
            )
        },
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(300.dp),
            state = grid,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val full: (androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope) -> GridItemSpan = { GridItemSpan(it.maxLineSpan) }
            item(span = full) { ExploreHeader(vm) }
            when {
                vm.boardError != null && vm.board.isEmpty() -> item(span = full) {
                    EmptyState(Icons.Rounded.CloudOff, MaterialShapes.Cookie9Sided, "Couldn't load the board", vm.boardError) {
                        Button(onClick = { vm.refreshBoard(pull = false) }) { Text("Try again") }
                    }
                }
                vm.board.isEmpty() && (vm.boardLoading || vm.boardRefreshing || vm.boardHasMore) -> item(span = full) {
                    Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(64.dp)) }
                }
                vm.board.isEmpty() -> item(span = full) {
                    EmptyState(Icons.Rounded.Check, MaterialShapes.Clover4Leaf, "Nothing here", "No profiles on the board match this filter yet.")
                }
                else -> {
                    items(vm.board, key = { it.username.lowercase() }) { item ->
                        BoardCard(item, favorited = item.username in vm.favoriteSet, onClick = { vm.openProfile(item.username) })
                    }
                    when {
                        vm.boardError != null -> item(span = full) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(vm.boardError!!, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { vm.loadMoreBoardRetry() }) { Text("Load more") }
                            }
                        }
                        vm.boardHasMore -> item(span = full) {
                            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExploreHeader(vm: AppViewModel) {
    var sortMenu by remember { mutableStateOf(false) }
    Column(Modifier.statusBarsPadding().padding(top = 20.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Explore", style = MaterialTheme.typography.displaySmallEmphasized)
                Text(
                    "Global board · ${if (vm.boardTotal > 0) vm.boardTotal else vm.board.size} profiles",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = { vm.randomProfile() }, enabled = !vm.randomLoading) {
                if (vm.randomLoading) LoadingIndicator(Modifier.size(20.dp)) else Icon(Icons.Rounded.Casino, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Random")
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                FilterChip(
                    selected = vm.boardSort != BoardSort.RECENT,
                    onClick = { sortMenu = true },
                    label = { Text(vm.boardSort.label) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Sort, null, Modifier.size(FilterChipDefaults.IconSize)) },
                    shape = CircleShape,
                )
                DropdownMenu(sortMenu, onDismissRequest = { sortMenu = false }, shape = RoundedCornerShape(20.dp)) {
                    BoardSort.entries.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s.label) },
                            leadingIcon = if (s == vm.boardSort) ({ Icon(Icons.Rounded.Check, null) }) else null,
                            onClick = { sortMenu = false; vm.chooseBoardSort(s) },
                        )
                    }
                }
            }
            BoardFilter.entries.forEach { f ->
                val sel = vm.boardFilter == f
                FilterChip(
                    selected = sel,
                    onClick = { vm.chooseBoardFilter(f) },
                    label = {
                        Text(
                            when (f) {
                                BoardFilter.ALL -> "All"
                                BoardFilter.CLEAN -> "Clean"
                                BoardFilter.RESTRICTED -> "Shadowbanned"
                                BoardFilter.SENSITIVE -> "Sensitive"
                                BoardFilter.UNKNOWN -> "Unknown"
                            },
                        )
                    },
                    leadingIcon = if (sel) ({ Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }) else null,
                    shape = CircleShape,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun BoardCard(item: ProfileSummary, favorited: Boolean, onClick: () -> Unit) {
    val sb = item.shadowban
    val level = sb?.let(::levelOf) ?: Level.UNKNOWN
    val accent = parseColor(item.accent) ?: levelColor(level).copy(alpha = 0.55f)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(4.2f)) {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(accent.copy(alpha = 0.75f), accent.copy(alpha = 0.18f)))))
                if (favorited) {
                    Row(Modifier.align(Alignment.TopEnd).padding(10.dp)) {
                        MiniBadge { Icon(Icons.Rounded.Favorite, "Favorite", Modifier.size(14.dp), tint = Color(0xFFFF4D7A)) }
                    }
                }
            }
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.Bottom) {
                Box(
                    Modifier.offset(y = (-22).dp).size(64.dp).clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(MaterialTheme.colorScheme.surfaceContainer).padding(3.dp),
                ) {
                    Avatar(item.avatar, size = 58.dp, shape = MaterialShapes.Cookie9Sided.toShape(), fallback = Net.avatarProxy(item.username))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f).padding(top = 8.dp).offset(y = (-6).dp)) {
                    NameLine(item.name, item.verified, MaterialTheme.typography.titleMediumEmphasized)
                    Text(
                        buildString {
                            append("@").append(item.username)
                            if (item.followers > 0) append(" · ").append(compact(item.followers)).append(" followers")
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp).offset(y = (-8).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusPill(sb?.summary ?: "Unknown", level)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Rounded.Visibility, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text(
                    listOfNotNull(compact(item.views.toLong()), sb?.lastChecked?.let(::ago)?.takeIf(String::isNotBlank)).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MiniBadge(content: @Composable () -> Unit) {
    Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.55f)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { content() }
    }
}
