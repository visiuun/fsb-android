@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vis.fsb.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Api
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vis.fsb.BuildConfig
import dev.vis.fsb.data.Downloader
import dev.vis.fsb.data.Updater
import dev.vis.fsb.data.ThemeMode

@Composable
fun AccountScreen(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    val context = LocalContext.current

    LazyColumn(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
    ) {
        val w = Modifier.widthIn(max = 720.dp).fillMaxWidth()
        item {
            Text("Account", style = MaterialTheme.typography.displaySmallEmphasized, modifier = w.statusBarsPadding().padding(top = 20.dp, bottom = 16.dp))
        }
        item {
            Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = w) {
                val a = vm.account
                when {
                    vm.signedIn && a == null -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        if (vm.accountLoading) LoadingIndicator() else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Signed in", style = MaterialTheme.typography.headlineSmallEmphasized, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = { vm.refreshAccount() }) { Text("Reload") }
                                OutlinedButton(onClick = { vm.signOut() }) { Text("Sign out") }
                            }
                        }
                    }
                    a != null -> Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                                MorphingBlob(
                                    listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Flower, MaterialShapes.Cookie12Sided),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), Modifier.size(76.dp),
                                )
                                Avatar(a.avatar, size = 60.dp, shape = MaterialShapes.Cookie9Sided.toShape())
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.name, style = MaterialTheme.typography.headlineSmallEmphasized, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 2)
                                val lines = listOfNotNull(
                                    a.xHandle?.let { "X: @$it" },
                                    a.discordName?.let { "Discord: $it" },
                                    a.email,
                                )
                                Text(
                                    lines.joinToString("  ·  ").ifBlank { "Signed in to FSB" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                )
                            }
                        }
                        a.xShadowban?.let { sb ->
                            Spacer(Modifier.height(14.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Your X account", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f))
                                StatusPill(sb.summary, levelOf(sb))
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val xh = a.xHandle
                            if (xh != null) {
                                Button(onClick = { vm.openProfile(xh) }) {
                                    Icon(Icons.Rounded.Person, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("My profile")
                                }
                            } else {
                                FilledTonalButton(onClick = { openInBrowser(context, "https://visiuun.com/fsb/apps") }) { Text("Link X account") }
                            }
                            OutlinedButton(onClick = { vm.signOut() }) {
                                Icon(Icons.AutoMirrored.Rounded.Logout, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Sign out", maxLines = 1, softWrap = false)
                            }
                        }
                    }
                    else -> Column(Modifier.padding(20.dp)) {
                        Text("Not signed in", style = MaterialTheme.typography.headlineSmallEmphasized, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Sign in with your FSB account (Discord or X) to sync favorites, track accounts with email alerts and get higher rate limits. You approve the app in your browser — it never sees your password.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                        )
                        Spacer(Modifier.height(18.dp))
                        Button(onClick = { vm.openLogin() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.Login, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Sign in to FSB", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }
        val acct = vm.account
        if (acct?.hidePresence != null) {
            item {
                Group("Privacy", w.padding(top = 24.dp)) {
                    SwitchItem(
                        "Hide my presence", "Don't show you as a live viewer on profiles you open on visiuun.com",
                        Icons.Rounded.VisibilityOff, MaterialShapes.Cookie6Sided, 0, 1, acct.hidePresence,
                    ) { v -> vm.setHidePresence(v) }
                }
            }
        }
        item {
            Group("Appearance", w.padding(top = 24.dp)) {
                val dyn = Build.VERSION.SDK_INT >= 31
                val n = if (dyn) 3 else 2
                ChoiceItem(
                    "Theme", Icons.Rounded.Contrast, MaterialShapes.Cookie4Sided, 0, n,
                    ThemeMode.entries, settings.theme,
                    { when (it) { ThemeMode.SYSTEM -> "System"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark" } },
                ) { v -> vm.updateSettings { it.copy(theme = v) } }
                SwitchItem(
                    "OLED black", "Pure black backgrounds in dark theme — saves battery on OLED screens",
                    Icons.Rounded.DarkMode, MaterialShapes.Cookie9Sided, 1, n, settings.oled,
                ) { v -> vm.updateSettings { it.copy(oled = v) } }
                if (dyn) {
                    SwitchItem(
                        "Dynamic color", "Use colors from your wallpaper (Material You)",
                        Icons.Rounded.Palette, MaterialShapes.Flower, 2, n, settings.dynamicColor,
                    ) { v -> vm.updateSettings { it.copy(dynamicColor = v) } }
                }
            }
        }
        item {
            Group("Media & downloads", w.padding(top = 20.dp)) {
                val n = 3
                SwitchItem(
                    "Hide sensitive media", "Blur posts flagged as sensitive until tapped",
                    Icons.Rounded.BlurOn, MaterialShapes.Puffy, 0, n, settings.blurSensitive,
                ) { v -> vm.updateSettings { it.copy(blurSensitive = v) } }
                SwitchItem(
                    "Original quality in viewer", "Load full-resolution images when opening media",
                    Icons.Rounded.HighQuality, MaterialShapes.Gem, 1, n, settings.highQuality,
                ) { v -> vm.updateSettings { it.copy(highQuality = v) } }
                InfoItem(
                    Icons.Rounded.Download, MaterialShapes.Cookie6Sided,
                    "Save location",
                    "Downloads/${Downloader.FOLDER}/  ·  named \"[artist] - filename_twitter.ext\", always original quality. Long-press a thumbnail in Gallery view to save it instantly.",
                    2, n,
                )
            }
        }
        item {
            Group("Updates", w.padding(top = 20.dp)) {
                val u = vm.update
                val r = u.release
                SwitchItem(
                    "Check for updates automatically", "Looks for new releases on GitHub when the app starts",
                    Icons.Rounded.Update, MaterialShapes.Cookie9Sided, 0, 2, settings.autoUpdate,
                ) { v -> vm.updateSettings { it.copy(autoUpdate = v) } }
                val status = when {
                    u.progress != null -> "Downloading ${(u.progress * 100).toInt()}%"
                    u.checking -> "Checking…"
                    u.message != null -> u.message
                    else -> "Installed version ${Updater.currentVersion}"
                }
                LinkItem(
                    Icons.Rounded.SystemUpdate, MaterialShapes.Flower,
                    if (r != null) "Install version ${r.version}" else "Check for updates", status, 1, 2,
                    trailing = false,
                ) { if (r != null) vm.installUpdate() else vm.checkForUpdate() }
            }
        }
        item {
            Group("About", w.padding(top = 20.dp)) {
                val links = listOf(
                    Triple("Guides", "What shadowbans are and how to fix them", "https://visiuun.com/fsb/guides") to (Icons.AutoMirrored.Rounded.MenuBook to MaterialShapes.Arch),
                    Triple("Live shadowban index", "Stats across every checked account", "https://visiuun.com/fsb/stats") to (Icons.Rounded.QueryStats to MaterialShapes.Cookie7Sided),
                    Triple("Developer API", "Free JSON API with app sign-in", "https://visiuun.com/fsb/docs/") to (Icons.Rounded.Api to MaterialShapes.Pill),
                    Triple("FAQ", "visiuun.com/fsb/faq", "https://visiuun.com/fsb/faq") to (Icons.Rounded.Info to MaterialShapes.Clover4Leaf),
                    Triple("Source code", Updater.REPO_URL.removePrefix("https://"), Updater.REPO_URL) to (Icons.Rounded.Code to MaterialShapes.Cookie6Sided),
                )
                val n = links.size + 1
                links.forEachIndexed { i, (t, ic) ->
                    LinkItem(ic.first, ic.second, t.first, t.second, i, n) { uri.openUri(t.third) }
                }
                InfoItem(
                    Icons.Rounded.Shield, MaterialShapes.Sunny, "FSB for Android ${BuildConfig.VERSION_NAME}",
                    "Material 3 Expressive · open-source libraries only (OkHttp, Coil, Media3). Shadowban data by FSB Sentinel (visiuun.com/fsb). Not affiliated with X Corp.",
                    links.size, n,
                )
            }
        }
    }
}

@Composable
private fun Group(title: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
        content()
    }
}

@Composable
private fun ItemSurface(index: Int, count: Int, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    if (onClick != null) {
        Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = segmentShape(index, count), color = MaterialTheme.colorScheme.surfaceContainer) { content() }
    } else {
        Surface(modifier = Modifier.fillMaxWidth(), shape = segmentShape(index, count), color = MaterialTheme.colorScheme.surfaceContainer) { content() }
    }
}

@Composable
private fun <T> ChoiceItem(
    title: String, icon: ImageVector, shape: RoundedPolygon, index: Int, count: Int,
    options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit,
) {
    ItemSurface(index, count) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapeBadge(icon, shape, size = 40.dp)
                Spacer(Modifier.width(14.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
            ConnectedChoice(options, selected, label, onSelect, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SwitchItem(
    title: String, subtitle: String?, icon: ImageVector, shape: RoundedPolygon,
    index: Int, count: Int, checked: Boolean, onChange: (Boolean) -> Unit,
) {
    ItemSurface(index, count, onClick = { onChange(!checked) }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ShapeBadge(icon, shape, size = 40.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                thumbContent = if (checked) {
                    { Icon(androidx.compose.material.icons.Icons.Rounded.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
                } else null,
            )
        }
    }
}

@Composable
private fun InfoItem(icon: ImageVector, shape: RoundedPolygon, title: String, body: String, index: Int, count: Int) {
    ItemSurface(index, count) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ShapeBadge(icon, shape, size = 40.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LinkItem(
    icon: ImageVector, shape: RoundedPolygon, title: String, body: String, index: Int, count: Int,
    trailing: Boolean = true, onClick: () -> Unit,
) {
    ItemSurface(index, count, onClick) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ShapeBadge(icon, shape, size = 40.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (trailing) Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
