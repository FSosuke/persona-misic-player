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
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import com.fsosuke.personamusic.data.youtubeId

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

enum class SearchKind(val defaultParams: String) {
    Songs("EgWKAQIIAWoKEAkQBRAKEAMQBA=="),
    Albums("EgWKAQIYAWoKEAkQChAFEAMQBA=="),
    Artists("EgWKAQIgAWoKEAkQChAFEAMQBA=="),
    Playlists("EgeKAQQoAEABagoQAxAEEAoQCRAF"),
}

data class SearchResults(
    val top: YtItem? = null,
    val songs: List<Song> = emptyList(),
    val artists: List<YtItem.ArtistItem> = emptyList(),
    val albums: List<YtItem.Collection> = emptyList(),
    val playlists: List<YtItem.Collection> = emptyList(),
    val moreParams: Map<SearchKind, String> = emptyMap(),
)

data class YtArtistPage(
    val browseId: String,
    val name: String,
    val thumbnail: String?,
    val songs: List<Song>,
    val shelves: List<HomeShelf>,
)

data class HomeShelf(val title: String, val items: List<YtItem>)

data class YtPlaylistPage(val title: String, val subtitle: String?, val thumbnail: String?, val songs: List<Song>)

/** Charts and new releases of one country (the Japan tab on Home). */
data class RegionFeed(
    val charts: List<HomeShelf> = emptyList(),
    val newReleases: List<HomeShelf> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val loadedAt: Long = 0,
)

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
    private val youTube: YouTubeMusic,
    private val scope: CoroutineScope,
    /** Region for Home recommendations, null = the phone's country. */
    private val homeRegion: () -> String? = { null },
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
        val region = homeRegion()
        var page: JSONObject = api.post("browse", cookie, JSONObject().put("browseId", "FEmusic_home"), region = region)
        for (i in 0 until HOME_PAGES) {
            shelves += parseShelves(page)
            val section = findFirst(page, "sectionListRenderer") ?: findFirst(page, "sectionListContinuation") ?: page
            page = nextPage(section, cookie, region) ?: break
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
                    ?: entry.optJSONObject("musicResponsiveListItemRenderer")?.let { parseResponsive(it) }
            }
            if (items.isEmpty()) null else HomeShelf(title, items)
        }

    /** Plain list shelves (charts use these for rankings). */
    private fun parseListShelves(page: JSONObject): List<HomeShelf> =
        findAll(page, "musicShelfRenderer").mapNotNull { shelf ->
            val title = shelf.obj("title").runsText() ?: return@mapNotNull null
            val items = findAll(shelf, "musicResponsiveListItemRenderer").mapNotNull { parseResponsive(it) }
            if (items.isEmpty()) null else HomeShelf(title, items)
        }

    // --- Region (Japan tab) ----------------------------------------------------------------

    private val _japan = MutableStateFlow(RegionFeed())
    val japan: StateFlow<RegionFeed> = _japan.asStateFlow()

    /** Charts and new releases for Japan, cached for an hour. */
    fun loadJapan(force: Boolean = false) {
        val current = _japan.value
        if (current.loading) return
        if (!force && current.error == null && System.currentTimeMillis() - current.loadedAt < REGION_STALE_MS) return
        _japan.value = current.copy(loading = true, error = null)
        scope.launch {
            val cookie = accounts.cookie
            val charts = async {
                runCatching {
                    val json = api.post(
                        "browse",
                        cookie,
                        JSONObject()
                            .put("browseId", "FEmusic_charts")
                            .put("formData", JSONObject().put("selectedValues", JSONArray().put(REGION_JAPAN))),
                        region = REGION_JAPAN,
                    )
                    parseShelves(json) + parseListShelves(json)
                }
            }
            val releases = async {
                runCatching {
                    parseShelves(api.post("browse", cookie, JSONObject().put("browseId", "FEmusic_new_releases"), region = REGION_JAPAN))
                }
            }
            val c = charts.await()
            val r = releases.await()
            _japan.value = RegionFeed(
                charts = c.getOrDefault(current.charts),
                newReleases = r.getOrDefault(current.newReleases),
                loading = false,
                error = (c.exceptionOrNull() ?: r.exceptionOrNull())?.let { it.localizedMessage ?: it.javaClass.simpleName },
                loadedAt = System.currentTimeMillis(),
            )
        }
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
    private suspend fun loadMix(spec: String, cookie: String?): YtPlaylistPage =
        loadQueue(spec.substringBefore('|'), null, spec.substringAfter('|', "").ifEmpty { null }, cookie)

    private suspend fun loadQueue(playlistId: String, videoId: String?, params: String?, cookie: String?): YtPlaylistPage {
        val body = JSONObject()
            .put("playlistId", playlistId)
            .put("isAudioOnly", true)
            .put("enablePersistentPlaylistPanel", true)
            .put("tunerSettingValue", "AUTOMIX_SETTING_NORMAL")
        if (params != null) body.put("params", params)
        if (videoId != null) body.put("videoId", videoId)
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

    // --- Search -----------------------------------------------------------------------------

    suspend fun suggestions(input: String): List<String> {
        val json = api.post("music/get_search_suggestions", accounts.cookie, JSONObject().put("input", input))
        return (findAll(json, "searchSuggestionRenderer") + findAll(json, "historySuggestionRenderer"))
            .mapNotNull { it.obj("suggestion").runsText() }
            .distinct()
            .take(8)
    }

    /** Full YouTube Music search: top result plus songs, artists, albums and playlists. */
    suspend fun search(query: String): SearchResults {
        val json = try {
            api.post("search", accounts.cookie, JSONObject().put("query", query))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Fall back to NewPipe's song search if the InnerTube search is unavailable.
            return SearchResults(songs = youTube.searchSongs(query))
        }
        val songs = mutableListOf<Song>()
        val artists = mutableListOf<YtItem.ArtistItem>()
        val albums = mutableListOf<YtItem.Collection>()
        val playlists = mutableListOf<YtItem.Collection>()
        val more = mutableMapOf<SearchKind, String>()

        fun add(item: YtItem?) {
            when (item) {
                is YtItem.SongItem -> songs += item.song
                is YtItem.ArtistItem -> artists += item
                is YtItem.Collection -> if (item.playlist.id.startsWith("MPRE")) albums += item else playlists += item
                null -> Unit
            }
        }

        val card = findFirst(json, "musicCardShelfRenderer")
        val top = card?.let { parseTopCard(it) }
        if (card != null) findAll(card, "musicResponsiveListItemRenderer").forEach { add(parseResponsive(it)) }
        findAll(json, "musicShelfRenderer").forEach { shelf ->
            val items = findAll(shelf, "musicResponsiveListItemRenderer").mapNotNull { parseResponsive(it) }
            val params = shelf.obj("bottomEndpoint", "searchEndpoint")?.str("params")
            val kind = items.firstOrNull()?.let { kindOf(it) }
            if (params != null && kind != null) more.putIfAbsent(kind, params)
            items.forEach { add(it) }
        }
        return SearchResults(
            top = top,
            songs = songs.distinctBy { it.id },
            artists = artists.distinctBy { it.browseId },
            albums = albums.distinctBy { it.playlist.id },
            playlists = playlists.distinctBy { it.playlist.id },
            moreParams = more,
        )
    }

    /** One category only ("all songs", "all albums", …), using the params the full search handed out. */
    suspend fun searchFiltered(query: String, kind: SearchKind, params: String?): List<YtItem> {
        val json = api.post("search", accounts.cookie, JSONObject().put("query", query).put("params", params ?: kind.defaultParams))
        return findAll(json, "musicResponsiveListItemRenderer").mapNotNull { parseResponsive(it) }
            .filter { kindOf(it) == kind }
    }

    private fun kindOf(item: YtItem): SearchKind = when (item) {
        is YtItem.SongItem -> SearchKind.Songs
        is YtItem.ArtistItem -> SearchKind.Artists
        is YtItem.Collection -> if (item.playlist.id.startsWith("MPRE")) SearchKind.Albums else SearchKind.Playlists
    }

    private fun parseTopCard(card: JSONObject): YtItem? {
        val titleRun = card.obj("title")?.optJSONArray("runs")?.objects()?.firstOrNull() ?: return null
        val title = card.obj("title").runsText() ?: return null
        val endpoint = titleRun.obj("navigationEndpoint") ?: card.obj("onTap") ?: return null
        val subtitleRuns = card.obj("subtitle")?.optJSONArray("runs")?.objects().orEmpty()
        val thumbnail = bestThumbnail(card.arr("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails"))
        return itemFromEndpoint(endpoint, title, card.obj("subtitle").runsText(), subtitleRuns, thumbnail)
    }

    // --- Radio ------------------------------------------------------------------------------

    /**
     * "Song radio": an endless-style mix of similar songs seeded by [seed]. Local songs are first
     * matched to their YouTube counterpart by title + artist.
     */
    suspend fun radio(seed: Song): List<Song> {
        val videoId = videoIdFor(seed) ?: return emptyList()
        return loadQueue("RDAMVM$videoId", videoId, null, accounts.cookie).songs
            .filter { it.youtubeId != videoId }
    }

    private suspend fun videoIdFor(song: Song): String? =
        song.youtubeId ?: runCatching { youTube.searchSongs("${song.title} ${song.artist}").firstOrNull()?.youtubeId }.getOrNull()

    // --- "Not interested" feedback ---------------------------------------------------------

    /** videoId → YouTube's "not interested" feedback token, collected while parsing feeds. */
    private val feedbackTokens = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun captureFeedback(renderer: JSONObject, videoId: String) {
        val token = findAll(renderer.obj("menu"), "menuServiceItemRenderer")
            .firstOrNull { it.str("icon", "iconType") == "NOT_INTERESTED" }
            ?.str("serviceEndpoint", "feedbackEndpoint", "feedbackToken")
            ?: return
        feedbackTokens[videoId] = token
    }

    /** Tells YouTube Music the user isn't interested in [song], if YouTube offered that option for it. */
    suspend fun notInterested(song: Song) {
        val cookie = accounts.cookie ?: return
        val token = song.youtubeId?.let { feedbackTokens[it] } ?: return
        api.post("feedback", cookie, JSONObject().put("feedbackTokens", JSONArray().put(token)))
    }

    // --- Sync (writes to the signed-in account) --------------------------------------------

    /** Mirrors the in-app heart to "Liked music" on YouTube Music. */
    suspend fun rate(song: Song, liked: Boolean) {
        val cookie = accounts.cookie ?: return
        val videoId = song.youtubeId ?: return
        api.post(
            if (liked) "like/like" else "like/removelike",
            cookie,
            JSONObject().put("target", JSONObject().put("videoId", videoId)),
        )
    }

    /** Playlists of the account that can take new songs (no albums, mixes or Liked Music). */
    fun editablePlaylists(): List<YtPlaylist> =
        _feed.value.playlists.filter { it.id.startsWith("VL") && it.id != LIKED_ID }

    suspend fun addToPlaylist(playlist: YtPlaylist, song: Song) {
        val cookie = accounts.cookie ?: throw IOException("Not signed in to YouTube Music")
        val videoId = videoIdFor(song) ?: throw IOException("Song not found on YouTube Music")
        val action = JSONObject().put("action", "ACTION_ADD_VIDEO").put("addedVideoId", videoId)
        val result = api.post(
            "browse/edit_playlist",
            cookie,
            JSONObject().put("playlistId", playlist.id.removePrefix("VL")).put("actions", JSONArray().put(action)),
        )
        if (result.optString("status") != "STATUS_SUCCEEDED") {
            throw IOException("\"${playlist.title}\" can't be edited")
        }
    }

    suspend fun createPlaylist(title: String, song: Song) {
        val cookie = accounts.cookie ?: throw IOException("Not signed in to YouTube Music")
        val videoId = videoIdFor(song) ?: throw IOException("Song not found on YouTube Music")
        api.post(
            "playlist/create",
            cookie,
            JSONObject().put("title", title).put("privacyStatus", "PRIVATE").put("videoIds", JSONArray().put(videoId)),
        )
        refresh(force = true)
    }

    /** Puts a play into the account's YouTube history so recommendations keep learning. */
    suspend fun registerPlayback(videoId: String) {
        val cookie = accounts.cookie ?: return
        val player = api.post("player", cookie, JSONObject().put("videoId", videoId))
        val base = player.str("playbackTracking", "videostatsPlaybackUrl", "baseUrl") ?: return
        val cpnAlphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"
        val cpn = (1..16).map { cpnAlphabet.random() }.joinToString("")
        val url = base.toHttpUrl().newBuilder()
            .addQueryParameter("ver", "2")
            .addQueryParameter("c", "WEB_REMIX")
            .addQueryParameter("cpn", cpn)
            .build()
        api.ping(url.toString(), cookie)
    }

    // --- Continuations ---------------------------------------------------------------------

    private suspend fun nextPage(container: Any, cookie: String?, region: String? = null): JSONObject? {
        findFirst(container, "continuationCommand")?.str("token")?.let { token ->
            return api.post("browse", cookie, JSONObject().put("continuation", token), region = region)
        }
        findFirst(container, "nextContinuationData")?.str("continuation")?.let { token ->
            return api.post("browse", cookie, JSONObject(), mapOf("ctoken" to token, "continuation" to token, "type" to "next"), region)
        }
        return null
    }

    // --- Renderer parsing ------------------------------------------------------------------

    private fun parseTwoRow(r: JSONObject): YtItem? {
        val title = r.obj("title").runsText() ?: return null
        val subtitleRuns = r.obj("subtitle")?.optJSONArray("runs")?.objects().orEmpty()
        val thumbnail = bestThumbnail(r.arr("thumbnailRenderer", "musicThumbnailRenderer", "thumbnail", "thumbnails"))
        val endpoint = r.obj("navigationEndpoint") ?: return null
        val item = itemFromEndpoint(endpoint, title, r.obj("subtitle").runsText(), subtitleRuns, thumbnail)
        if (item is YtItem.SongItem) item.song.youtubeId?.let { captureFeedback(r, it) }
        return item
    }

    /** Turns a navigation endpoint (+ display data) into a song, collection or artist. */
    private fun itemFromEndpoint(
        endpoint: JSONObject,
        title: String,
        subtitle: String?,
        subtitleRuns: List<JSONObject>,
        thumbnail: String?,
    ): YtItem? {
        endpoint.obj("watchEndpoint")?.str("videoId")?.let { videoId ->
            val artists = subtitleRuns.filter { pageType(it) == ARTIST }.joinToString(", ") { it.optString("text") }
            val duration = subtitleRuns.map { it.optString("text") }.lastOrNull { DURATION.matches(it) }
            return YtItem.SongItem(
                ytSong(videoId, title, artists.ifEmpty { subtitle ?: "Unknown artist" }, null, thumbnail, duration)
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

    /** List rows in search results can be songs, but also albums, artists or playlists. */
    private fun parseResponsive(r: JSONObject): YtItem? {
        val endpoint = r.obj("navigationEndpoint")
        if (endpoint?.obj("browseEndpoint") != null) {
            val columns = r.arr("flexColumns")?.objects().orEmpty().map { it.obj("musicResponsiveListItemFlexColumnRenderer", "text") }
            val title = columns.firstOrNull().runsText() ?: return null
            val subtitle = columns.drop(1).mapNotNull { it.runsText() }.joinToString(" • ").ifEmpty { null }
            val thumbnail = bestThumbnail(r.arr("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails"))
            return itemFromEndpoint(endpoint, title, subtitle, emptyList(), thumbnail)
        }
        return parseSong(r)?.let { YtItem.SongItem(it) }
    }

    private fun parseSong(r: JSONObject, fallbackThumbnail: String? = null, fallbackArtist: String? = null): Song? {
        val columns = r.arr("flexColumns")?.objects().orEmpty().map { it.obj("musicResponsiveListItemFlexColumnRenderer", "text") }
        val firstRuns = columns.firstOrNull()?.optJSONArray("runs")
        val videoId = r.str("playlistItemData", "videoId")
            ?: r.str("overlay", "musicItemThumbnailOverlayRenderer", "content", "musicPlayButtonRenderer", "playNavigationEndpoint", "watchEndpoint", "videoId")
            ?: firstRuns.str(0, "navigationEndpoint", "watchEndpoint", "videoId")
            ?: return null
        captureFeedback(r, videoId)
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
        private const val REGION_STALE_MS = 60 * 60 * 1000L
        private const val REGION_JAPAN = "JP"
        private val DURATION = Regex("^\\d{1,2}(:\\d{2}){1,2}$")
    }
}
