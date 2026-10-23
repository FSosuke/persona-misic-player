package com.fsosuke.personamusic.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fsosuke.personamusic.ui.artist.ArtistScreen
import com.fsosuke.personamusic.ui.download.DownloadsScreen
import com.fsosuke.personamusic.ui.components.LocalAppContainer
import com.fsosuke.personamusic.ui.components.LocalOpenArtist
import com.fsosuke.personamusic.ui.components.MiniPlayer
import com.fsosuke.personamusic.ui.components.PersonaBottomBar
import com.fsosuke.personamusic.ui.components.Tab
import com.fsosuke.personamusic.ui.home.HomeScreen
import com.fsosuke.personamusic.ui.library.LibraryScreen
import com.fsosuke.personamusic.ui.player.NowPlayingScreen
import com.fsosuke.personamusic.ui.player.QueueScreen
import com.fsosuke.personamusic.ui.playlist.PlaylistDetailScreen
import com.fsosuke.personamusic.ui.playlist.PlaylistsScreen
import com.fsosuke.personamusic.ui.settings.CustomizeScreen
import com.fsosuke.personamusic.ui.stats.StatsScreen
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors
import com.fsosuke.personamusic.ui.youtube.YtLoginScreen
import com.fsosuke.personamusic.ui.youtube.YtPlaylistScreen

private object Routes {
    const val PLAYER = "player"
    const val QUEUE = "queue"
    const val DOWNLOADS = "downloads"
    const val ARTIST = "artist?name={name}&id={id}"
    fun artist(name: String, id: String?) = "artist?name=${Uri.encode(name)}" + (id?.let { "&id=${Uri.encode(it)}" } ?: "")
    const val STATS = "stats"
    const val CUSTOMIZE = "customize"
    const val PLAYLIST = "playlist/{id}"
    const val YT_PLAYLIST = "ytplaylist/{id}"
    const val YT_LOGIN = "ytlogin"
    fun playlist(id: Long) = "playlist/$id"
    fun ytPlaylist(id: String) = "ytplaylist/${Uri.encode(id)}"
}

@Composable
fun PersonaMusicApp() {
    val container = LocalAppContainer.current
    val colors = LocalPersonaColors.current
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val playerState by container.player.state.collectAsStateWithLifecycle()
    val position by container.player.positionMs.collectAsStateWithLifecycle()

    val showChrome = route != Routes.PLAYER && route != Routes.YT_LOGIN && route != Routes.QUEUE
    val selectedTab = Tab.entries.firstOrNull { it.route == route }
        ?: if (route == Routes.PLAYLIST || route == Routes.YT_PLAYLIST || route == Routes.DOWNLOADS) Tab.Playlists else null

    var chromeHeightPx by remember { mutableIntStateOf(0) }
    val chromeHeight = with(LocalDensity.current) { chromeHeightPx.toDp() }
    val padding = PaddingValues(bottom = if (showChrome) chromeHeight else 0.dp)

    fun selectTab(tab: Tab) {
        nav.navigate(tab.route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val openArtist: (String, String?) -> Unit = { name, id -> nav.navigate(Routes.artist(name, id)) }

    CompositionLocalProvider(LocalOpenArtist provides openArtist) {
    Box(Modifier.fillMaxSize().background(colors.background)) {
        NavHost(
            navController = nav,
            startDestination = Tab.Home.route,
            enterTransition = { fadeIn(tween(180)) },
            exitTransition = { fadeOut(tween(120)) },
        ) {
            composable(Tab.Home.route) {
                HomeScreen(
                    onOpenSettings = { nav.navigate(Routes.CUSTOMIZE) },
                    onOpenYtPlaylist = { nav.navigate(Routes.ytPlaylist(it)) },
                    onSignIn = { nav.navigate(Routes.YT_LOGIN) },
                    contentPadding = padding,
                )
            }
            composable(Tab.Library.route) { LibraryScreen(contentPadding = padding) }
            composable(Tab.Playlists.route) {
                PlaylistsScreen(
                    onOpen = { nav.navigate(Routes.playlist(it)) },
                    onOpenYt = { nav.navigate(Routes.ytPlaylist(it)) },
                    onOpenDownloads = { nav.navigate(Routes.DOWNLOADS) },
                    contentPadding = padding,
                )
            }
            composable(Routes.PLAYLIST, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                PlaylistDetailScreen(
                    playlistId = entry.arguments?.getLong("id") ?: 0L,
                    onBack = { nav.popBackStack() },
                    contentPadding = padding,
                )
            }
            composable(
                Routes.PLAYER,
                enterTransition = { slideInVertically(tween(260)) { it } },
                exitTransition = { slideOutVertically(tween(220)) { it } },
                popExitTransition = { slideOutVertically(tween(220)) { it } },
            ) {
                NowPlayingScreen(
                    onClose = { nav.popBackStack() },
                    onOpenQueue = { nav.navigate(Routes.QUEUE) },
                    onOpenStats = { nav.navigate(Routes.STATS) },
                    onOpenCustomize = { nav.navigate(Routes.CUSTOMIZE) },
                )
            }
            composable(Routes.YT_PLAYLIST, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                YtPlaylistScreen(
                    playlistId = entry.arguments?.getString("id").orEmpty(),
                    onBack = { nav.popBackStack() },
                    contentPadding = padding,
                )
            }
            composable(Routes.YT_LOGIN) {
                YtLoginScreen(onDone = { nav.popBackStack() }, onBack = { nav.popBackStack() })
            }
            composable(
                Routes.QUEUE,
                enterTransition = { slideInVertically(tween(240)) { it } },
                popExitTransition = { slideOutVertically(tween(200)) { it } },
            ) { QueueScreen(onClose = { nav.popBackStack() }) }
            composable(
                Routes.ARTIST,
                arguments = listOf(
                    navArgument("name") { type = NavType.StringType; defaultValue = "" },
                    navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entry ->
                ArtistScreen(
                    name = entry.arguments?.getString("name").orEmpty(),
                    browseId = entry.arguments?.getString("id"),
                    onBack = { nav.popBackStack() },
                    onOpenYtPlaylist = { nav.navigate(Routes.ytPlaylist(it)) },
                    contentPadding = padding,
                )
            }
            composable(Routes.DOWNLOADS) { DownloadsScreen(onBack = { nav.popBackStack() }, contentPadding = padding) }
            composable(Routes.STATS) { StatsScreen(onBack = { nav.popBackStack() }, contentPadding = padding) }
            composable(Routes.CUSTOMIZE) { CustomizeScreen(
                    onBack = { nav.popBackStack() },
                    onSignIn = { nav.navigate(Routes.YT_LOGIN) },
                    contentPadding = padding,
                ) }
        }

        AnimatedVisibility(
            visible = showChrome,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(Modifier.fillMaxWidth().onSizeChanged { chromeHeightPx = it.height }) {
                MiniPlayer(
                    state = playerState,
                    positionMs = position,
                    onOpen = { nav.navigate(Routes.PLAYER) { launchSingleTop = true } },
                    onTogglePlay = container.player::togglePlay,
                    onNext = container.player::next,
                )
                PersonaBottomBar(selected = selectedTab, onSelect = ::selectTab)
            }
        }
    }
    }
}
