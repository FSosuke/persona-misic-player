package com.fsosuke.personamusic.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.fsosuke.personamusic.data.AppSettings
import com.fsosuke.personamusic.data.DownloadEntry
import com.fsosuke.personamusic.data.MusicDao
import com.fsosuke.personamusic.data.Song
import com.fsosuke.personamusic.data.SongSource
import com.fsosuke.personamusic.data.youtubeId
import com.fsosuke.personamusic.youtube.YouTubeMusic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File
import java.io.IOException

/**
 * Saves YouTube songs as tagged audio files into a folder the user picked (Storage Access
 * Framework), so they play offline here and show up properly in any other player.
 */
class DownloadManager(
    private val context: Context,
    private val client: OkHttpClient,
    private val youTube: YouTubeMusic,
    private val dao: MusicDao,
    private val settings: AppSettings,
    private val scope: CoroutineScope,
) {
    /** A queued / running / failed download. `progress` is null while the size is unknown. */
    data class Task(val song: Song, val progress: Float? = null, val error: String? = null)

    private val _tasks = MutableStateFlow<Map<String, Task>>(emptyMap())
    val tasks: StateFlow<Map<String, Task>> = _tasks.asStateFlow()

    /** songId → content uri of the saved file. */
    val downloaded: StateFlow<Map<String, String>> = dao.downloads()
        .map { list -> list.associate { it.songId to it.fileUri } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    val downloadedSongs = dao.downloadedSongs()

    private val queue = Channel<Song>(Channel.UNLIMITED)

    init {
        TagOptionSingleton.getInstance().isAndroid = true
        repeat(PARALLEL) {
            scope.launch {
                for (song in queue) process(song)
            }
        }
    }

    fun uriFor(songId: String): String? = downloaded.value[songId]

    /** Remembers the picked folder (with persistent read/write access). */
    fun setFolder(tree: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                tree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        settings.setDownloadFolder(tree.toString())
    }

    /** "primary:Music/Persona" → "Music/Persona". */
    fun folderLabel(): String? = settings.downloadFolder.value?.let { uri ->
        runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(uri)).substringAfter(':').ifEmpty { "/" } }.getOrNull()
    }

    /** Queues YouTube songs that aren't downloaded or downloading yet. Returns how many were queued. */
    fun enqueue(songs: List<Song>): Int {
        val fresh = songs
            .filter { it.source == SongSource.YOUTUBE && it.id !in downloaded.value }
            .filter { _tasks.value[it.id]?.let { task -> task.error != null } ?: true }
            .distinctBy { it.id }
        if (fresh.isEmpty()) return 0
        _tasks.update { current -> current + fresh.associate { it.id to Task(it) } }
        scope.launch {
            dao.upsertSongs(fresh)
            fresh.forEach { queue.send(it) }
        }
        return fresh.size
    }

    fun dismissFailed(songId: String) = _tasks.update { it - songId }

    suspend fun remove(songId: String) {
        val uri = downloaded.value[songId] ?: return
        withContext(Dispatchers.IO) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(uri)) }
            dao.deleteDownload(songId)
        }
    }

    /** Drops the entry if the file was deleted outside the app. */
    suspend fun verify(songId: String) = withContext(Dispatchers.IO) {
        val uri = downloaded.value[songId] ?: return@withContext
        val exists = runCatching { context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.use { true } ?: false }.getOrDefault(false)
        if (!exists) dao.deleteDownload(songId)
    }

    private suspend fun process(song: Song) {
        val result = runCatching { withContext(Dispatchers.IO) { download(song) } }
        result.onSuccess { entry ->
            dao.upsertDownload(entry)
            _tasks.update { it - song.id }
        }.onFailure { error ->
            _tasks.update { it + (song.id to Task(song, error = error.localizedMessage ?: error.javaClass.simpleName)) }
        }
    }

    private fun download(song: Song): DownloadEntry {
        val tree = settings.downloadFolder.value?.let(Uri::parse) ?: throw IOException("No download folder chosen")
        val videoId = song.youtubeId ?: throw IOException("Not a YouTube song")
        val stream = youTube.resolveDownload(videoId)

        val temp = File(context.cacheDir, "downloads").apply { mkdirs() }.resolve("$videoId.${stream.extension}")
        try {
            fetchInChunks(stream.url, temp) { fraction ->
                _tasks.update { it + (song.id to Task(song, progress = fraction)) }
            }
            if (stream.extension == "m4a") runCatching { writeTags(temp, song) }

            val resolver = context.contentResolver
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val name = fileName(song) + "." + stream.extension
            val target = DocumentsContract.createDocument(resolver, parent, stream.mimeType, name)
                ?: throw IOException("Could not create file in the download folder")
            resolver.openOutputStream(target, "w")?.use { out -> temp.inputStream().use { it.copyTo(out) } }
                ?: throw IOException("Could not write to the download folder")
            return DownloadEntry(song.id, target.toString(), temp.length())
        } finally {
            temp.delete()
        }
    }

    /** YouTube throttles or cuts long single requests, so fetch the file in ranged chunks. */
    private fun fetchInChunks(url: String, target: File, onProgress: (Float?) -> Unit) {
        target.outputStream().use { out ->
            var position = 0L
            var total: Long? = null
            while (true) {
                val known = total
                if (known != null && position >= known) break
                val end = position + CHUNK_SIZE - 1
                val request = Request.Builder().url(url).header("Range", "bytes=$position-$end").build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Download failed (HTTP ${response.code})")
                    total = response.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
                        ?: response.body?.contentLength()?.takeIf { response.code == 200 && it > 0 }
                    val bytes = response.body?.bytes() ?: throw IOException("Empty response")
                    if (bytes.isEmpty()) return
                    out.write(bytes)
                    position += bytes.size
                    if (response.code == 200) total = position // server ignored the range: we got everything
                }
                onProgress(total?.let { position.toFloat() / it })
            }
        }
    }

    private fun writeTags(file: File, song: Song) {
        val audio = AudioFileIO.read(file)
        val tag = audio.tagOrCreateAndSetDefault
        tag.setField(FieldKey.TITLE, song.title)
        tag.setField(FieldKey.ARTIST, song.artist)
        song.album?.let { tag.setField(FieldKey.ALBUM, it) }
        song.artworkUri?.let { url ->
            val bytes = client.newCall(Request.Builder().url(url).build()).execute().use { if (it.isSuccessful) it.body?.bytes() else null }
            if (bytes != null) {
                val artwork = ArtworkFactory.getNew()
                artwork.binaryData = bytes
                artwork.mimeType = "image/jpeg"
                tag.deleteArtworkField()
                tag.setField(artwork)
            }
        }
        audio.commit()
    }

    private fun fileName(song: Song): String =
        "${song.artist} - ${song.title}".replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), "_").trim().take(120)

    private companion object {
        const val PARALLEL = 2
        const val CHUNK_SIZE = 1L shl 20 // 1 MiB
    }
}
