/*
 * Copyright (C) 2026 The PenguinOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.keyguard.ui.composable.media

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal object MotionArt {
    enum class Kind(val key: String, val targetWidth: Int, val suffix: String) {
        SQUARE("motionDetailSquare", 768, ""),
        TALL("motionDetailTall", 830, "-tall"),
    }

    private const val TAG = "LockscreenMotionArt"
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/138.0 Mobile Safari/537.36"
    private const val MAX_BYTES = 24L * 1024 * 1024
    private const val MAX_CACHED = 40

    private val albums = ConcurrentHashMap<String, String>()
    private const val NONE = ""

    suspend fun find(context: Context, artist: String, title: String, kind: Kind): File? =
        withContext(Dispatchers.IO) {
            if (artist.isBlank() || title.isBlank()) return@withContext null
            val dir = File(context.createDeviceProtectedStorageContext().cacheDir, "motion_art")
            val key = "$artist\u0000$title\u0000$kind"
            val known = albums[key]
            if (known != null) {
                return@withContext known.takeIf { it != NONE }
                    ?.let { File(dir, "$it${kind.suffix}.mp4") }
                    ?.takeIf { it.exists() }
            }
            try {
                val album = findAlbum(artist, title)
                if (album == null) {
                    albums[key] = NONE
                    return@withContext null
                }
                val (id, page) = album
                val cached = File(dir, "$id${kind.suffix}.mp4")
                if (!cached.exists() && !download(page, kind, cached)) {
                    albums[key] = NONE
                    return@withContext null
                }
                albums[key] = id
                cached.setLastModified(System.currentTimeMillis())
                trim(dir)
                cached
            } catch (e: IOException) {
                Log.w(TAG, "Motion art lookup failed: $e")
                null
            }
        }

    private fun findAlbum(artist: String, title: String): Pair<String, String>? {
        val country = Locale.getDefault().country.lowercase().ifEmpty { "us" }
        val term = URLEncoder.encode("$artist $title", "UTF-8")
        val json =
            JSONObject(
                fetch(
                    "https://itunes.apple.com/search?term=$term&entity=song&limit=10" +
                        "&country=$country"
                )
            )
        val results = json.optJSONArray("results") ?: return null
        val wantedArtist = normalise(artist)
        val wantedTitle = normalise(title)
        for (i in 0 until results.length()) {
            val song = results.getJSONObject(i)
            val songArtist = normalise(song.optString("artistName"))
            val songTitle = normalise(song.optString("trackName"))
            val artistMatches =
                songArtist.contains(wantedArtist) || wantedArtist.contains(songArtist)
            val titleMatches = songTitle.startsWith(wantedTitle) || wantedTitle.startsWith(songTitle)
            if (!artistMatches || !titleMatches) continue
            val id = song.optLong("collectionId").takeIf { it > 0 } ?: continue
            val page = song.optString("collectionViewUrl").substringBefore('?')
            if (page.isNotEmpty()) return id.toString() to page
        }
        return null
    }

    private fun normalise(text: String) =
        text.lowercase().replace(Regex("\\(.*?\\)|\\[.*?]"), "").replace(Regex("[^\\p{L}\\p{N}]"), "")

    private fun download(page: String, kind: Kind, target: File): Boolean {
        val html = fetch(page)
        val start = html.indexOf("\"${kind.key}\"")
        if (start < 0) return false
        val video =
            Regex("\"video\":\"(https://[^\"]+?\\.m3u8)\"")
                .find(html.substring(start, minOf(html.length, start + 4000)))
                ?.groupValues
                ?.get(1) ?: return false

        val master = fetch(video).lines()
        var best: String? = null
        var bestDistance = Int.MAX_VALUE
        for (i in master.indices) {
            val line = master[i]
            if (!line.startsWith("#EXT-X-STREAM-INF") || !line.contains("avc1")) continue
            val width =
                Regex("RESOLUTION=(\\d+)x").find(line)?.groupValues?.get(1)?.toInt() ?: continue
            val uri = master.getOrNull(i + 1)?.trim().orEmpty()
            if (uri.isEmpty() || uri.startsWith("#")) continue
            val distance = abs(width - kind.targetWidth)
            if (distance < bestDistance) {
                best = URL(URL(video), uri).toString()
                bestDistance = distance
            }
        }
        best ?: return false

        val map =
            Regex("#EXT-X-MAP:URI=\"([^\"]+)\"").find(fetch(best))?.groupValues?.get(1)
                ?: return false
        target.parentFile?.mkdirs()
        val partial = File(target.path + ".part")
        val connection = open(URL(URL(best), map).toString())
        try {
            if (connection.contentLengthLong > MAX_BYTES) return false
            connection.inputStream.use { input ->
                partial.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
        return partial.length() in 1..MAX_BYTES && partial.renameTo(target)
    }

    private fun trim(dir: File) {
        val files = dir.listFiles { file -> file.name.endsWith(".mp4") } ?: return
        files.sortedByDescending { it.lastModified() }.drop(MAX_CACHED).forEach { it.delete() }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", USER_AGENT)
        }

    private fun fetch(url: String): String {
        val connection = open(url)
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${connection.responseCode} for $url")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
