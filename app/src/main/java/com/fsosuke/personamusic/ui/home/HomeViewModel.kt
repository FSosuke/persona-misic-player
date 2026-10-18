package com.fsosuke.personamusic.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fsosuke.personamusic.data.MusicRepository
import com.fsosuke.personamusic.data.Song
import com.fsosuke.personamusic.youtube.YouTubeMusic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface OnlineResults {
    data object Idle : OnlineResults
    data object Loading : OnlineResults
    data class Loaded(val songs: List<Song>) : OnlineResults
    data class Failed(val message: String) : OnlineResults
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: MusicRepository,
    private val youTube: YouTubeMusic,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val trimmedQuery = _query.map { it.trim() }.distinctUntilChanged()

    val online: StateFlow<OnlineResults> = trimmedQuery
        .debounce(450)
        .flatMapLatest { q ->
            if (q.isEmpty()) {
                flowOf(OnlineResults.Idle)
            } else {
                flow {
                    emit(OnlineResults.Loading)
                    emit(
                        runCatching { youTube.searchSongs(q) }
                            .fold(
                                onSuccess = { OnlineResults.Loaded(it) },
                                onFailure = { OnlineResults.Failed(it.localizedMessage ?: it.javaClass.simpleName) },
                            )
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnlineResults.Idle)

    val local: StateFlow<List<Song>> = trimmedQuery
        .flatMapLatest { q -> if (q.isEmpty()) flowOf(emptyList()) else repository.searchLocal(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recent = repository.recent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val mostPlayed = repository.mostPlayed().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val songLog = repository.songLog().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) {
        _query.value = value
    }

    /** Persist songs before playing so history and stats can join on them. */
    fun remember(songs: List<Song>) {
        viewModelScope.launch { repository.remember(songs) }
    }
}
