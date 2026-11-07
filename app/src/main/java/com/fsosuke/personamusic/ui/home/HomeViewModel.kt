package com.fsosuke.personamusic.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fsosuke.personamusic.data.MusicRepository
import com.fsosuke.personamusic.data.SearchHistory
import com.fsosuke.personamusic.data.Song
import com.fsosuke.personamusic.youtube.SearchKind
import com.fsosuke.personamusic.youtube.SearchResults
import com.fsosuke.personamusic.youtube.YtItem
import com.fsosuke.personamusic.youtube.YtMusicLibrary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SearchFilter(val label: String, val kind: SearchKind?) {
    All("All", null),
    Songs("Songs", SearchKind.Songs),
    Artists("Artists", SearchKind.Artists),
    Albums("Albums", SearchKind.Albums),
    Playlists("Playlists", SearchKind.Playlists),
    Library("Library", null),
}

sealed interface Load<out T> {
    data object Idle : Load<Nothing>
    data object Loading : Load<Nothing>
    data class Loaded<T>(val value: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: MusicRepository,
    private val ytLibrary: YtMusicLibrary,
    private val history: SearchHistory,
) : ViewModel() {

    /** What is in the text field. */
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** The query that was actually searched (enter / suggestion tap). Empty = not searching. */
    private val _submitted = MutableStateFlow("")
    val submitted: StateFlow<String> = _submitted.asStateFlow()

    private val _filter = MutableStateFlow(SearchFilter.All)
    val filter: StateFlow<SearchFilter> = _filter.asStateFlow()

    val searchHistory: StateFlow<List<String>> = history.entries

    val suggestions: StateFlow<List<String>> = combine(_query, _submitted) { q, s -> q.trim().takeIf { it.isNotEmpty() && it != s } }
        .distinctUntilChanged()
        .debounce(180)
        .flatMapLatest { q ->
            if (q == null) flowOf(emptyList()) else flow { emit(runCatching { ytLibrary.suggestions(q) }.getOrDefault(emptyList())) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val results: StateFlow<Load<SearchResults>> = _submitted
        .flatMapLatest { q ->
            if (q.isEmpty()) flowOf<Load<SearchResults>>(Load.Idle) else flow<Load<SearchResults>> {
                emit(Load.Loading)
                emit(
                    runCatching { ytLibrary.search(q) }.fold<Load<SearchResults>, SearchResults>(
                        { Load.Loaded(it) },
                        { Load.Failed(it.localizedMessage ?: it.javaClass.simpleName) },
                    )
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Load.Idle)

    /** The full list behind a category tab (Songs / Artists / Albums / Playlists). */
    val filtered: StateFlow<Load<List<YtItem>>> = combine(_submitted, _filter, results) { q, f, r -> Triple(q, f, r) }
        .distinctUntilChanged { a, b -> a.first == b.first && a.second == b.second && (a.third is Load.Loaded) == (b.third is Load.Loaded) }
        .flatMapLatest { (q, f, r) ->
            val kind = f.kind
            if (q.isEmpty() || kind == null) flowOf<Load<List<YtItem>>>(Load.Idle) else flow<Load<List<YtItem>>> {
                emit(Load.Loading)
                val params = (r as? Load.Loaded)?.value?.moreParams?.get(kind)
                emit(
                    runCatching { ytLibrary.searchFiltered(q, kind, params) }.fold<Load<List<YtItem>>, List<YtItem>>(
                        { Load.Loaded(it) },
                        { Load.Failed(it.localizedMessage ?: it.javaClass.simpleName) },
                    )
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Load.Idle)

    val local: StateFlow<List<Song>> = _submitted
        .flatMapLatest { q -> if (q.isEmpty()) flowOf(emptyList()) else repository.searchLocal(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recent = repository.recent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val mostPlayed = repository.mostPlayed().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val songLog = repository.songLog().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) {
        _query.value = value
        if (value.isBlank()) _submitted.value = ""
    }

    fun submit(value: String = _query.value) {
        val q = value.trim()
        if (q.isEmpty()) return
        _query.value = q
        _submitted.value = q
        _filter.value = SearchFilter.All
        history.add(q)
    }

    fun setFilter(filter: SearchFilter) {
        _filter.value = filter
    }

    fun removeFromHistory(q: String) = history.remove(q)
    fun clearHistory() = history.clear()

    /** Persist songs before playing so history and stats can join on them. */
    fun remember(songs: List<Song>) {
        viewModelScope.launch { repository.remember(songs) }
    }
}
