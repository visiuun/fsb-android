package dev.vis.fsb.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Saves media into the public Downloads/FSB folder.
 *
 * Files are written by the app itself (MediaStore on Android 10+, direct file on 8–9) rather than
 * through the system DownloadManager, because several vendor download managers rename files.
 * Names are exactly `[artist] - <filename>_twitter.<extension>`, e.g. `[visiuun] - HK8h4oTWYAAJifi_twitter.jpg`.
 */
object Downloader {
    const val FOLDER = "FSB"

    data class Result(val saved: Int, val skipped: Int, val failed: Int, val lastName: String?)

    /** Original-quality URL for a twimg photo; videos are returned unchanged. */
    fun bestUrl(m: Media): String {
        val u = m.url
        if (!u.contains("pbs.twimg.com/media/")) return u
        val uri = Uri.parse(u)
        val path = uri.path ?: return u
        val dot = path.lastIndexOf('.')
        return if (dot > 0) {
            "https://pbs.twimg.com${path}?name=orig"
        } else {
            val fmt = uri.getQueryParameter("format") ?: "jpg"
            "https://pbs.twimg.com$path?format=$fmt&name=orig"
        }
    }

    fun fileName(artist: String, m: Media): String {
        val uri = Uri.parse(bestUrl(m))
        val last = uri.lastPathSegment.orEmpty()
        val dot = last.lastIndexOf('.')
        val base = if (dot > 0) last.substring(0, dot) else last
        val ext = when {
            dot > 0 -> last.substring(dot + 1)
            uri.getQueryParameter("format") != null -> uri.getQueryParameter("format")!!
            m.isVideo -> "mp4"
            else -> "jpg"
        }.lowercase()
        val a = clean(artist.removePrefix("@")).ifBlank { "unknown" }
        val b = clean(base).ifBlank { "media" }.take(120)
        return "[$a] - ${b}_twitter.$ext"
    }

    private fun clean(s: String) = s.replace(Regex("""[\\/:*?"<>|\r\n\t]"""), "_").trim()

    private fun mimeOf(name: String, m: Media) =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', ""))
            ?: if (m.isVideo) "video/mp4" else "image/jpeg"

    /** Downloads every item (3 at a time). Already-saved files are skipped. */
    suspend fun download(context: Context, items: List<Pair<String, Media>>): Result = withContext(Dispatchers.IO) {
        val gate = Semaphore(3)
        val outcomes = coroutineScope {
            items.distinctBy { it.second.url }.map { (artist, m) ->
                async {
                    gate.withPermit {
                        val url = bestUrl(m)
                        if (url.contains(".m3u8")) return@withPermit Triple(false, false, null)
                        val name = fileName(artist, m)
                        if (exists(context, name)) return@withPermit Triple(false, true, name)
                        val ok = runCatching { save(context, url, name, mimeOf(name, m)) }.isSuccess
                        Triple(ok, false, name)
                    }
                }
            }.awaitAll()
        }
        Result(
            saved = outcomes.count { it.first },
            skipped = outcomes.count { it.second },
            failed = outcomes.count { !it.first && !it.second },
            lastName = outcomes.lastOrNull { it.first || it.second }?.third,
        )
    }

    private val relPath = "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER/"

    private fun exists(context: Context, name: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return legacyFile(name).exists()
        return runCatching {
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
                arrayOf(name, relPath),
                null,
            )?.use { it.count > 0 } ?: false
        }.getOrDefault(false)
    }

    private fun legacyFile(name: String): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "$FOLDER/$name")

    private fun save(context: Context, url: String, name: String, mime: String) {
        val req = Request.Builder().url(url).header("User-Agent", Net.userAgent).build()
        Net.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cr = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IOException("insert failed")
                try {
                    cr.openOutputStream(uri)?.use { out -> body.byteStream().copyTo(out) } ?: throw IOException("no stream")
                    cr.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                } catch (t: Throwable) {
                    cr.delete(uri, null, null)
                    throw t
                }
            } else {
                val f = legacyFile(name)
                f.parentFile?.mkdirs()
                val tmp = File(f.parentFile, "$name.part")
                try {
                    tmp.outputStream().use { out -> body.byteStream().copyTo(out) }
                    if (!tmp.renameTo(f)) throw IOException("rename failed")
                } catch (t: Throwable) {
                    tmp.delete()
                    throw t
                }
                MediaScannerConnection.scanFile(context, arrayOf(f.absolutePath), arrayOf(mime), null)
            }
        }
    }

    fun forPost(p: Post): List<Pair<String, Media>> =
        p.media.map { p.authorHandle to it } + p.quote?.media.orEmpty().map { p.quote!!.username.ifBlank { p.authorHandle } to it }
}
