@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vis.fsb.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * App sign-in with the FSB device flow: show the code, open the approval page in a Custom Tab
 * and wait while [AppViewModel] polls `/auth/token`. FSB never sees a password in the app.
 */
@Composable
fun LoginScreen(vm: AppViewModel) {
    val st = vm.login
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val code = st.code
    var autoOpened by rememberSaveable(code?.userCode) { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(code) { while (code != null) { now = System.currentTimeMillis(); delay(1000) } }
    LaunchedEffect(code?.userCode) {
        if (code != null && !autoOpened) {
            autoOpened = true
            delay(600) // let the user see the code first
            openInBrowser(context, code.verificationUriComplete)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text("Sign in to FSB", style = MaterialTheme.typography.titleLargeEmphasized)
        }
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when {
                    st.error != null -> {
                        EmptyState(Icons.Rounded.Warning, MaterialShapes.SoftBurst, "Sign-in didn't work", st.error) {
                            Button(onClick = { vm.startLogin() }) { Text("Try again") }
                        }
                    }
                    st.expired -> {
                        EmptyState(Icons.Rounded.Timer, MaterialShapes.Cookie9Sided, "Code expired", "Codes are valid for 10 minutes and work once.") {
                            Button(onClick = { vm.startLogin() }) { Text("Get a new code") }
                        }
                    }
                    code == null || st.finishing -> {
                        Spacer(Modifier.height(80.dp))
                        LoadingIndicator(Modifier.size(72.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(if (st.finishing) "Signing you in…" else "Getting a sign-in code…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> {
                        ShapeBadge(
                            Icons.Rounded.Key, MaterialShapes.Cookie12Sided, size = 88.dp,
                            container = MaterialTheme.colorScheme.primaryContainer, content = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(20.dp))
                        Text(
                            "Approve this device on visiuun.com",
                            style = MaterialTheme.typography.headlineSmallEmphasized, textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Sign in with Discord or X in the browser, check the code matches, then tap Approve. This screen continues automatically.",
                            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(24.dp))
                        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(start = 24.dp, end = 8.dp, top = 18.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    code.userCode,
                                    style = MaterialTheme.typography.displaySmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = 4.sp),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                )
                                IconButton(onClick = {
                                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("FSB code", code.userCode))) }
                                }) { Icon(Icons.Rounded.ContentCopy, "Copy code") }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        val left = ((code.expiresAt - now) / 1000).coerceAtLeast(0)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LoadingIndicator(Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Waiting for approval · expires in %d:%02d".format(left / 60, left % 60),
                                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { openInBrowser(context, code.verificationUriComplete) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Open sign-in page")
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { vm.startLogin() }, modifier = Modifier.fillMaxWidth()) { Text("Get a new code") }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "On another device? Go to ${code.verificationUri.removePrefix("https://")} and enter the code.",
                            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Opens a URL in a Custom Tab of the user's browser. The browser package is set explicitly so the
 * visiuun.com/fsb link isn't routed back into this app (which handles visiuun.com/fsb links).
 */
fun openInBrowser(context: Context, url: String) {
    val uri = Uri.parse(url)
    val pm = context.packageManager
    val browser = runCatching {
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        pm.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            ?.takeIf { it != "android" && it != context.packageName }
            ?: pm.queryIntentActivities(probe, 0).map { it.activityInfo.packageName }.firstOrNull { it != context.packageName }
    }.getOrNull()
    try {
        val tab = CustomTabsIntent.Builder().setShowTitle(true).build()
        if (browser != null) tab.intent.setPackage(browser)
        tab.launchUrl(context, uri)
    } catch (_: Exception) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { if (browser != null) setPackage(browser); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        }
    }
}
