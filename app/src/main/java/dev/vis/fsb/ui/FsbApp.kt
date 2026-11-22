@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vis.fsb.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vis.fsb.data.Media
import dev.vis.fsb.ui.theme.FsbTheme

/** Download entry point that handles the legacy storage permission on Android 8–9. */
val LocalDownload = staticCompositionLocalOf<(List<Pair<String, Media>>) -> Unit> { {} }

private data class TabSpec(val tab: Tab, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    TabSpec(Tab.EXPLORE, "Explore", Icons.Outlined.Explore, Icons.Rounded.Explore),
    TabSpec(Tab.SEARCH, "Search", Icons.Outlined.Search, Icons.Rounded.Search),
    TabSpec(Tab.FAVORITES, "Favorites", Icons.Outlined.FavoriteBorder, Icons.Rounded.Favorite),
    TabSpec(Tab.ACCOUNT, "Account", Icons.Outlined.AccountCircle, Icons.Rounded.AccountCircle),
)

@Composable
fun FsbApp(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    FsbTheme(settings) {
        val context = LocalContext.current
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(Unit) {
            vm.messages.collect { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(it) }
        }

        var pending by remember { mutableStateOf<List<Pair<String, Media>>?>(null) }
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            val p = pending
            pending = null
            if (ok && p != null) vm.download(p) else if (!ok) vm.toast("Storage permission is needed to save files")
        }
        val download: (List<Pair<String, Media>>) -> Unit = { items ->
            if (Build.VERSION.SDK_INT < 29 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            ) {
                pending = items
                permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                vm.download(items)
            }
        }

        UpdateDialog(vm.update, onUpdate = vm::installUpdate, onLater = vm::dismissUpdate)

        BackHandler(enabled = vm.stack.isNotEmpty()) { vm.back() }
        BackHandler(enabled = vm.stack.isEmpty() && vm.tab != Tab.EXPLORE) { vm.selectTab(Tab.EXPLORE) }

        CompositionLocalProvider(LocalDownload provides download) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val wide = maxWidth >= 600.dp
                    val top = vm.stack.lastOrNull()
                    val immersive = top is Route.Viewer || top is Route.Login
                    Row(Modifier.fillMaxSize()) {
                        AnimatedVisibility(wide && !immersive) {
                            NavigationRail(
                                Modifier.fillMaxHeight(),
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                header = { Spacer(Modifier.statusBarsPadding().height(12.dp)) },
                            ) {
                                tabs.forEach { t ->
                                    val sel = vm.tab == t.tab && vm.stack.isEmpty()
                                    NavigationRailItem(
                                        selected = sel,
                                        onClick = { vm.selectTab(t.tab) },
                                        icon = { Icon(if (sel) t.selectedIcon else t.icon, t.label) },
                                        label = { Text(t.label) },
                                    )
                                }
                            }
                        }
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            Box(Modifier.weight(1f)) {
                                Content(vm)
                            }
                            AnimatedVisibility(
                                !wide && !immersive,
                                enter = slideInVertically { it },
                                exit = slideOutVertically { it },
                            ) {
                                ShortNavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                                    tabs.forEach { t ->
                                        val sel = vm.tab == t.tab
                                        ShortNavigationBarItem(
                                            selected = sel,
                                            onClick = { vm.selectTab(t.tab) },
                                            icon = { Icon(if (sel) t.selectedIcon else t.icon, t.label) },
                                            label = { Text(t.label) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    SnackbarHost(
                        snackbar,
                        Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (wide || immersive) 8.dp else 72.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Content(vm: AppViewModel) {
    val holder = rememberSaveableStateHolder()
    val top = vm.stack.lastOrNull()
    val key: Any = top ?: vm.tab
    val depth = vm.stack.size
    var lastDepth by remember { mutableStateOf(depth) }
    val forward = depth >= lastDepth
    LaunchedEffect(depth) { lastDepth = depth }

    AnimatedContent(
        targetState = key,
        transitionSpec = {
            when {
                targetState is Route.Viewer -> (fadeIn(tween(220)) + scaleIn(initialScale = 0.92f)) togetherWith fadeOut(tween(150))
                initialState is Route.Viewer -> fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                targetState is Tab && initialState is Tab -> fadeIn(tween(220)) togetherWith fadeOut(tween(120))
                forward -> (slideInHorizontally { it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 8 } + fadeOut())
                else -> (slideInHorizontally { -it / 8 } + fadeIn()) togetherWith (slideOutHorizontally { it / 4 } + fadeOut())
            }
        },
        label = "nav",
    ) { k ->
        holder.SaveableStateProvider(if (k is Route.Viewer) "viewer" else k.toString()) {
            when (k) {
                is Route.ProfileRoute -> ProfileScreen(vm, k.handle)
                is Route.Viewer -> MediaViewer(vm, k.items, k.start)
                Route.Login -> LoginScreen(vm)
                Tab.EXPLORE -> ExploreScreen(vm)
                Tab.SEARCH -> SearchScreen(vm)
                Tab.FAVORITES -> FavoritesScreen(vm)
                Tab.ACCOUNT -> AccountScreen(vm)
                else -> Unit
            }
        }
    }
}
