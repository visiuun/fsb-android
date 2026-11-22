@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package dev.vis.fsb.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vis.fsb.data.Net
import dev.vis.fsb.data.ProfileSummary

@Composable
fun SearchScreen(vm: AppViewModel) {
    val recent by vm.recent.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val clean = vm.cleanQuery(vm.query)
    val direct = AppViewModel.handleRx.matches(clean) && vm.results.none { it.username.equals(clean, true) }
    val submit = {
        val exact = vm.results.firstOrNull { it.username.equals(clean, true) }
        when {
            exact != null -> { focus.clearFocus(); vm.openProfile(exact.username) }
            AppViewModel.handleRx.matches(clean) -> { focus.clearFocus(); vm.openProfile(clean) }
            else -> vm.results.firstOrNull()?.let { focus.clearFocus(); vm.openProfile(it.username) }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val w = Modifier.widthIn(max = 720.dp).fillMaxWidth()
        item {
            Column(w.statusBarsPadding().padding(top = 20.dp, bottom = 16.dp)) {
                Text("Search", style = MaterialTheme.typography.displaySmallEmphasized)
                Text(
                    "Check any X account for shadowbans and browse its media",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                TextField(
                    value = vm.query,
                    onValueChange = vm::onQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("@username, name, link or X ID") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (vm.searching) LoadingIndicator(Modifier.size(28.dp))
                            if (vm.query.isNotEmpty()) IconButton(onClick = { vm.onQuery("") }) { Icon(Icons.Rounded.Close, "Clear") }
                        }
                    },
                    singleLine = true,
                    shape = CircleShape,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onSearch = { submit() }),
                )
            }
        }

        val count = vm.results.size + if (direct) 1 else 0
        if (direct) {
            item {
                UserRow(
                    ProfileSummary(clean, "Check @$clean", null, null, false, 0, null, null, 0, 0, null), 0, count, w,
                    leading = { ShapeBadge(Icons.Rounded.AlternateEmail, MaterialShapes.Cookie9Sided, size = 48.dp) },
                    subtitle = "Run a live shadowban test",
                ) { focus.clearFocus(); vm.openProfile(clean) }
            }
        }
        itemsIndexed(vm.results, key = { _, u -> "r_" + u.username.lowercase() }) { i, u ->
            UserRow(u, i + if (direct) 1 else 0, count, w) { focus.clearFocus(); vm.openProfile(u.username) }
        }
        if (vm.searchError != null && vm.query.isNotBlank()) {
            item { Text(vm.searchError!!, color = MaterialTheme.colorScheme.error, modifier = w.padding(8.dp)) }
        }

        if (vm.query.isBlank()) {
            if (recent.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Rounded.PersonSearch, MaterialShapes.Sunny, "Find an account",
                        "Type a handle to run search ban, suggestion ban, ghost ban and reply deboosting tests, then view and download their media.",
                    )
                }
            } else {
                item {
                    Row(w.padding(start = 8.dp, top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        TextButton(onClick = vm::clearRecent) { Text("Clear") }
                    }
                }
                itemsIndexed(recent, key = { _, h -> "h_" + h.lowercase() }) { i, h ->
                    Surface(
                        onClick = { vm.openProfile(h) },
                        shape = segmentShape(i, recent.size),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = w.padding(bottom = 2.dp),
                    ) {
                        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(Net.avatarProxy(h), size = 40.dp, shape = MaterialShapes.Cookie7Sided.toShape())
                            Spacer(Modifier.width(14.dp))
                            Icon(Icons.Rounded.History, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(6.dp))
                            Text("@$h", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            IconButton(onClick = { vm.removeRecent(h) }) { Icon(Icons.Rounded.Close, "Remove") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserRow(
    u: ProfileSummary,
    index: Int,
    count: Int,
    modifier: Modifier,
    leading: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = segmentShape(index, count),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.padding(bottom = 2.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) leading() else Avatar(u.avatar, size = 48.dp, shape = MaterialShapes.Cookie9Sided.toShape(), fallback = Net.avatarProxy(u.username))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                NameLine(u.name, u.verified, MaterialTheme.typography.titleMedium)
                Text(
                    subtitle ?: buildString {
                        append("@").append(u.username)
                        if (u.followers > 0) append(" · ").append(compact(u.followers)).append(" followers")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            u.shadowban?.let { sb ->
                Spacer(Modifier.width(8.dp))
                StatusPill(sb.summary, levelOf(sb))
            }
            if (trailing != null) trailing() else {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun FavoritesScreen(vm: AppViewModel) {
    val pull = rememberPullToRefreshState()
    val hasItems = vm.favorites.isNotEmpty() || vm.tracked.isNotEmpty()
    PullToRefreshBox(
        isRefreshing = vm.listsLoading && hasItems,
        onRefresh = { vm.loadLists() },
        state = pull,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pull, isRefreshing = vm.listsLoading && hasItems,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
            )
        },
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val w = Modifier.widthIn(max = 720.dp).fillMaxWidth()
            item {
                Column(w.statusBarsPadding().padding(top = 20.dp, bottom = 16.dp)) {
                    Text("Favorites", style = MaterialTheme.typography.displaySmallEmphasized)
                    Text(
                        if (vm.signedIn) "${vm.favorites.size} / 6 favorites · ${vm.tracked.size} tracked with email alerts"
                        else "Pin up to 6 profiles and track accounts with email alerts",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when {
                !vm.signedIn -> item {
                    EmptyState(
                        Icons.AutoMirrored.Rounded.Login, MaterialShapes.Heart, "Sign in to use favorites",
                        "Favorites and tracked accounts sync with your FSB account on visiuun.com.",
                    ) {
                        Button(onClick = { vm.openLogin() }) { Text("Sign in to FSB") }
                    }
                }
                vm.listsLoading && !hasItems -> item {
                    Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(56.dp)) }
                }
                !hasItems -> item {
                    EmptyState(
                        Icons.Rounded.FavoriteBorder, MaterialShapes.Heart, "No favorites yet",
                        "Tap the heart on any profile to pin it here, or the bell to get email alerts when its status changes.",
                    )
                }
                else -> {
                    profileSection(vm, "Favorites", vm.favorites, w, "Remove favorite") { vm.toggleFavorite(it) }
                    profileSection(vm, "Tracked · email alerts", vm.tracked, w, "Stop tracking") { vm.toggleTracked(it) }
                }
            }
        }
    }
}

private fun LazyListScope.profileSection(
    vm: AppViewModel,
    title: String,
    list: List<ProfileSummary>,
    w: Modifier,
    removeLabel: String,
    onRemove: (String) -> Unit,
) {
    if (list.isEmpty()) return
    item(key = "h_$title") {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = w.padding(start = 8.dp, top = 8.dp, bottom = 8.dp))
    }
    itemsIndexed(list, key = { _, u -> title + u.username.lowercase() }) { i, u ->
        UserRow(
            u, i, list.size, w,
            trailing = { IconButton(onClick = { onRemove(u.username) }) { Icon(Icons.Rounded.Close, removeLabel) } },
        ) { vm.openProfile(u.username) }
    }
}
