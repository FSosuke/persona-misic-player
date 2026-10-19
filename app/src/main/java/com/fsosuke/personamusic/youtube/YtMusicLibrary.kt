package com.fsosuke.personamusic.youtube

import com.fsosuke.personamusic.data.Song
import com.fsosuke.personamusic.data.SongSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONObject

/** A YouTube Music collection: a browsable playlist/album, or a radio-style mix (`watch:` prefix). */
data class YtPlaylist(
    val id: String,
    val title: String,
    val subtitle: String?,
    val thumbnail: String?,
)

sealed interface YtItem {
    data class SongItem(val song: Song) : YtItem
    data class Collection(val playlist: YtPlaylist) : YtItem
    data class ArtistItem(val name: String, val browseId: String, val thumbnail: String?) : YtItem
}

data class YtArtistPage(
    val browseId: String,
    val name: String,
    val thumbnail: String?,
    val songs: List<Song>,
    val shelves: List<HomeShelf>,
)

data class HomeShelf(val title: String, val items: List<YtItem>)

data class YtPlaylistPage(val title: String, val subtitle: String?, val thumbnail: String?, val songs: List<Song>)

data class YtFeed(
    val home: List<HomeShelf> = emptyList(),
    val playlists: List<YtPlaylist> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val loadedAt: Long = 0,
)

/** Read-only view of the signed-in user's YouTube Music: personalised home and library playlists. */
class YtMusicLibrary(
    private val api: InnerTube,
    private val accounts: YtAccountManager,
    private val scope: CoroutineScope,
) {
    private val _feed = MutableStateFlow(YtFeed())
    val feed: StateFlow<YtFeed> = _feed.asStateFlow()
    private var refreshJob: Job? = null

    init {
        scope.launch {
            accounts.account.map { it != null }.distinctUntilChanged().collect { signedIn ->
                if (signedIn) refresh(force = true) else {
                    refreshJob?.cancel()
                    _feed.value = YtFeed()
                }
            }
        }
    }

    fun refresh(force: Boolean = false) {
        val cookie = accounts.cookie ?: return
        val current = _feed.value
        if (current.loading) return
        if (!force && current.error == null && System.currentTimeMillis() - current.loadedAt < STALE_MS) return
        _feed.value = current.copy(loading = true, error = null)
        refreshJob = scope.launch {
            val home = async { runCatching { loadHome(cookie) } }
            val playlists = async { runCatching { loadLibraryPlaylists(cookie) } }
            val h = home.await()
            val p = playlists.await()
            _feed.value = YtFeed(
                home = h.getOrDefault(current.home),
                playlists = p.getOrDefault(current.playlists),
                loading = false,
                error = (h.exceptionOrNull() ?: p.exceptionOrNull())?.let { it.localizedMessage ?: it.javaClass.simpleName },
                loadedAt = System.currentTimeMillis(),
            )
        }
    }

    suspend fun playlist(id: String): YtPlaylistPage {
        val cookie = accounts.cookie
        return if (id.startsWith(WATCH_PREFIX)) loadMix(id.removePrefix(WATCH_PREFIX), cookie) else loadBrowsePlaylist(id, cookie)
    }

    /** Artist page by channel id, or found via search when only the name is known. */
    suspend fun artist(name: String, browseId: String?): YtArtistPage? {
        val cookie = accounts.cookie
        val id = browseId ?: findArtistId(name, cookie) ?: return null
        val json = api.post("browse", cookie, JSONObject().put("browseId", id))
        val header = findFirst(json, "musicImmersiveHeaderRenderer")
            ?: findFirst(json, "musicVisualHeaderRenderer")
            ?: findFirst(json, "musicResponsiveHeaderRenderer")
        val title = header.obj("title").runsText() ?: name
        val thumbnail = header?.let { bestThumbnail(findThumbnails(it)) }
        val songs = findFirst(json, "musicShelfRenderer")
            ?.let { shelf -> findAll(shelf, "musicResponsiveListItemRenderer").mapNotNull { parseSong(it, null, title) } }
            .orEmpty()
        return YtArtistPage(id, title, thumbnail, songs.distinctBy { it.id }, parseShelves(json))
    }

    private suspend fun findArtistId(name: String, cookie: String?): String? {
        val json = api.post("search", cookie, JSONObject().put("query", name))
        val artists = findAll(json, "browseEndpoint").filter {
            it.str("browseEndpointContextSupportedConfigs", "browseEndpointContextMusicConfig", "pageType") == ARTIST
        }
        return artists.firstOrNull()?.str("browseId")
    }

    // --- Home ------------------------------------------------------------------------------

    private suspend fun loadHome(cookie: String): List<HomeShelf> {
        val shelves = mutableListOf<HomeShelf>()
        var page: JSONObject = api.post("browse", cookie, JSONObject().put("browseId", "FEmusic_home"))
        for (i in 0 until HOME_PAGES) {
            shelves += parseShelves(page)
            val section = findFirst(page, "sectionListRenderer") ?: findFirst(page, "sectionListContinuation") ?: page
            page = nextPage(section, cookie) ?: break
        }
        return shelves
    }

    private fun parseShelves(page: JSONObject): List<HomeShelf> =
        (findAll(page, "musicCarouselShelfRenderer") + findAll(page, "musicImmersiveCarouselShelfRenderer")).mapNotNull { shelf ->
            val header = shelf.obj("header", "musicCarouselShelfBasicHeaderRenderer")
                ?: shelf.obj("header", "musicImmersiveCarouselShelfBasicHeaderRenderer")
            val title = header?.obj("title").runsText() ?: return@mapNotNull null
            val items = shelf.arr("contents")?.objects().orEmpty().mapNotNull { entry ->
                entry.optJSONObject("musicTwoRowItemRenderer")?.let { parseTwoRow(it) }
                    ?: entry.optJSONObject("musicResponsiveListItemRenderer")?.let { r -> parseSong(r)?.let { YtItem.SongItem(it) } }
            }
            if (items.isEmpty()) null else HomeShelf(title, items)
        }

    // --- Library ---------------------------------------------------------------------------

    private suspend fun loadLibraryPlaylists(cookie: String): List<YtPlaylist> {
        val result = mutableListOf<YtPlaylist>()
        var page: JSONObject = api.post("browse", cookie, JSONObject().put("browseId", "FEmusic_liked_playlists"))
        for (i in 0 until MAX_PAGES) {
            result += findAll(page, "musicTwoRowItemRenderer").mapNotNull { (parseTwoRow(it) as? YtItem.Collection)?.playlist }
            val grid = findFirst(page, "gridRenderer") ?: findFirst(page, "gridContinuation") ?: page
            page = nextPage(grid, cookie) ?: break
        }
        val playlists = result.distinctBy { it.id }
        // "Liked music" is normally listed, but make sure it is always there.
        return if (playlists.any { it.id == LIKED_ID }) playlists
        else listOf(YtPlaylist(LIKED_ID, "Liked Music", null, null)) + playlists
    }

    private suspend fun loadBrowsePlaylist(browseId: String, cookie: String?): YtPlaylistPage {
        val first = api.post("browse", cookie, JSONObject().put("browseId", browseId))
        val header = findFirst(first, "musicResponsiveHeaderRenderer")
            ?: findFirst(first, "musicDetailHeaderRenderer")
            ?: findFirst(first, "musicEditablePlaylistDetailHeaderRenderer")
            ?: findFirst(first, "musicImmersiveHeaderRenderer")
        val title = header.obj("title").runsText() ?: findFirst(header, "title").runsText() ?: "Playlist"
        val thumbnail = bestThumbnail(findFirst(header, "thumbnail")?.let { findThumbnails(it) })
        val subtitle = header.obj("subtitle").runsText() ?: header.obj("secondSubtitle").runsText()
        val albumArtist = if (browseId.startsWith("MPRE")) header.obj("straplineTextOne").runsText() else null

        val songs = mutableListOf<Song>()
        var shelf: Any = findFirst(first, "musicPlaylistShelfRenderer") ?: findFirst(first, "musicShelfRenderer") ?: first
        for (i in 0 until MAX_PAGES) {
            songs += findAll(shelf, "musicResponsiveListItemRenderer").mapNotNull { parseSong(it, thumbnail, albumArtist) }
            shelf = nextPage(shelf, cookie) ?: break
        }
        return YtPlaylistPage(title, subtitle, thumbnail, songs.distinctBy { it.id })
    }

    /** Mixes ("My Supermix", radios) are queues from the `next` endpoint, not browse pages. */
    private suspend fun loadMix(spec: String, cookie: String?): YtPlaylistPage {
        val playlistId = spec.substringBefore('|')
        val params = spec.substringAfter('|', "").ifEmpty { null }
        val body = JSONObject()
            .put("playlistId", playlistId)
            .put("isAudioOnly", true)
            .put("enablePersistentPlaylistPanel", true)
            .put("tunerSettingValue", "AUTOMIX_SETTING_NORMAL")
        if (params != null) body.put("params", params)
        val json = api.post("next", cookie, body)
        val songs = findAll(json, "playlistPanelVideoRenderer").mapNotNull { r ->
            val videoId = r.optString("videoId").ifEmpty { null } ?: return@mapNotNull null
            val byline = r.obj("longBylineText")?.optJSONArray("runs")?.objects().orEmpty()
            val artists = byline.filter { pageType(it) == ARTIST }.joinToString(", ") { it.optString("text") }
            ytSong(
                videoId = videoId,
                title = r.obj("title").runsText() ?: return@mapNotNull null,
                artist = artists.ifEmpty { byline.firstOrNull()?.optString("text") ?: "Unknown artist" },
                album = byline.firstOrNull { pageType(it) == ALBUM }?.optString("text"),
                thumbnail = bestThumbnail(r.arr("thumbnail", "thumbnails")),
                duration = r.obj("lengthText").runsText(),
            )
        }
        val title = findFirst(json, "playlistPanelRenderer").obj("title").runsText() ?: "Mix"
        return YtPlaylistPage(title, null, songs.firstOrNull()?.artworkUri, songs.distinctBy { it.id })
    }

    // --- Continuations ---------------------------------------------------------------------

    private suspend fun nextPage(container: Any, cookie: String?): JSONObject? {
        findFirst(container, "continuationCommand")?.str("token")?.let { token ->
            return api.post("browse", cookie, JSONObject().put("continuation", token))
        }
        findFirst(container, "nextContinuationData")?.str("continuation")?.let { token ->
            return api.post("browse", cookie, JSONObject(), mapOf("ctoken" to token, "continuation" to token, "type" to "next"))
        }
        return null
    }

    // --- Renderer parsing ------------------------------------------------------------------

    private fun parseTwoRow(r: JSONObject): YtItem? {
        val title = r.obj("title").runsText() ?: return null
        val subtitleRuns = r.obj("subtitle")?.optJSONArray("runs")?.objects().orEmpty()
        val subtitle = r.obj("subtitle").runsText()
        val thumbnail = bestThumbnail(r.arr("thumbnailRenderer", "musicThumbnailRenderer", "thumbnail", "thumbnails"))
        val endpoint = r.obj("navigationEndpoint") ?: return null

        endpoint.obj("watchEndpoint")?.str("videoId")?.let { videoId ->
            val artists = subtitleRuns.filter { pageType(it) == ARTIST }.joinToString(", ") { it.optString("text") }
            return YtItem.SongItem(
                ytSong(videoId, title, artists.ifEmpty { subtitle ?: "Unknown artist" }, null, thumbnail, null)
            )
        }
        endpoint.obj("watchPlaylistEndpoint")?.let { watch ->
            val playlistId = watch.str("playlistId") ?: return null
            val params = watch.str("params")
            val id = WATCH_PREFIX + playlistId + (params?.let { "|$it" } ?: "")
            return YtItem.Collection(YtPlaylist(id, title, subtitle, thumbnail))
        }
        val browse = endpoint.obj("browseEndpoint") ?: return null
        val browseId = browse.str("browseId") ?: return null
        return when (browse.str("browseEndpointContextSupportedConfigs", "browseEndpointContextMusicConfig", "pageType")) {
            PLAYLIST, ALBUM, "MUSIC_PAGE_TYPE_AUDIOBOOK" -> YtItem.Collection(YtPlaylist(browseId, title, subtitle, thumbnail))
            ARTIST -> YtItem.ArtistItem(title, browseId, thumbnail)
            else -> null
        }
    }

    private fun parseSong(r: JSONObject, fallbackThumbnail: String? = null, fallbackArtist: String? = null): Song? {
        val columns = r.arr("flexColumns")?.objects().orEmpty().map { it.obj("musicResponsiveListItemFlexColumnRenderer", "text") }
        val firstRuns = columns.firstOrNull()?.optJSONArray("runs")
        val videoId = r.str("playlistItemData", "videoId")
            ?: r.str("overlay", "musicItemThumbnailOverlayRenderer", "content", "musicPlayButtonRenderer", "playNavigationEndpoint", "watchEndpoint", "videoId")
            ?: firstRuns.str(0, "navigationEndpoint", "watchEndpoint", "videoId")
            ?: return null
        val title = columns.firstOrNull().runsText() ?: return null
        val otherRuns = columns.drop(1).flatMap { it?.optJSONArray("runs")?.objects().orEmpty() }
        val artists = otherRuns.filter { pageType(it) == ARTIST }.joinToString(", ") { it.optString("text") }
        val duration = r.str("fixedColumns", 0, "musicResponsiveListItemFixedColumnRenderer", "text", "runs", 0, "text")
            ?: otherRuns.map { it.optString("text") }.lastOrNull { DURATION.matches(it) }
        return ytSong(
            videoId = videoId,
            title = title,
            artist = artists.ifEmpty { fallbackArtist ?: otherRuns.firstOrNull()?.optString("text") ?: "Unknown artist" },
            album = otherRuns.firstOrNull { pageType(it) == ALBUM }?.optString("text"),
            thumbnail = bestThumbnail(r.arr("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails")) ?: fallbackThumbnail,
            duration = duration,
        )
    }

    private fun findThumbnails(node: JSONObject): org.json.JSONArray? {
        var found: org.json.JSONArray? = null
        fun walk(n: Any?) {
            if (found != null) return
            when (n) {
                is JSONObject -> n.keys().forEach { k ->
                    val v = n.opt(k)
                    if (k == "thumbnails" && v is org.json.JSONArray) found = v else walk(v)
                }
                is org.json.JSONArray -> for (i in 0 until n.length()) walk(n.opt(i))
            }
        }
        walk(node)
        return found
    }

    private fun pageType(run: JSONObject) =
        run.str("navigationEndpoint", "browseEndpoint", "browseEndpointContextSupportedConfigs", "browseEndpointContextMusicConfig", "pageType")

    private fun ytSong(videoId: String, title: String, artist: String, album: String?, thumbnail: String?, duration: String?) = Song(
        id = Song.YT_PREFIX + videoId,
        title = title,
        artist = artist,
        album = album,
        artworkUri = thumbnail,
        durationMs = parseDuration(duration),
        source = SongSource.YOUTUBE,
        uri = YouTubeMusic.watchUrl(videoId),
    )

    private fun parseDuration(text: String?): Long {
        if (text == null || !DURATION.matches(text)) return 0
        return text.split(":").fold(0L) { acc, part -> acc * 60 + part.toLong() } * 1000
    }

    companion object {
        const val WATCH_PREFIX = "watch:"
        const val LIKED_ID = "VLLM"
        private const val ARTIST = "MUSIC_PAGE_TYPE_ARTIST"
        private const val ALBUM = "MUSIC_PAGE_TYPE_ALBUM"
        private const val PLAYLIST = "MUSIC_PAGE_TYPE_PLAYLIST"
        private const val HOME_PAGES = 3
        private const val MAX_PAGES = 12
        private const val STALE_MS = 30 * 60 * 1000L
        private val DURATION = Regex("^\\d{1,2}(:\\d{2}){1,2}$")
    }
}
