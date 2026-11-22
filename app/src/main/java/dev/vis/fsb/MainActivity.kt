package dev.vis.fsb

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dev.vis.fsb.ui.AppViewModel
import dev.vis.fsb.ui.FsbApp

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        setContent { FsbApp(vm) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Opens profiles from visiuun.com/fsb links or shared x.com / twitter.com links. */
    private fun handle(intent: Intent?) {
        intent ?: return
        val text = when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        } ?: return
        // Approval pages (visiuun.com/fsb/apps) belong in the browser, not the app.
        if (Regex("""visiuun\.com/fsb/(apps|docs|api)""").containsMatchIn(text)) return
        val handle = Regex("""[?&]target=([A-Za-z0-9_]{1,15})""").find(text)?.groupValues?.get(1)
            ?: Regex("""(?:x|twitter)\.com/([A-Za-z0-9_]{1,15})""").find(text)?.groupValues?.get(1)
                ?.takeUnless { it.lowercase() in setOf("i", "home", "search", "explore", "intent", "share", "hashtag") }
            ?: Regex("""^@?([A-Za-z0-9_]{1,15})$""").find(text.trim())?.groupValues?.get(1)
        if (handle != null) vm.openProfile(handle)
        else if (text.contains("view=global")) vm.selectTab(dev.vis.fsb.ui.Tab.EXPLORE)
    }
}
