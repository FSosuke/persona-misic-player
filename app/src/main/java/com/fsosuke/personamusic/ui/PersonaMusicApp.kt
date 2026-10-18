package com.fsosuke.personamusic.ui

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
import com.fsosuke.personamusic.ui.components.LocalAppContainer
import com.fsosuke.personamusic.ui.components.MiniPlayer
import com.fsosuke.personamusic.ui.components.PersonaBottomBar
import com.fsosuke.personamusic.ui.components.Tab
import com.fsosuke.personamusic.ui.home.HomeScreen
import com.fsosuke.personamusic.ui.library.LibraryScreen
import com.fsosuke.personamusic.ui.player.NowPlayingScreen
import com.fsosuke.personamusic.ui.playlist.PlaylistDetailScreen
import com.fsosuke.personamusic.ui.playlist.PlaylistsScreen
import com.fsosuke.personamusic.ui.settings.CustomizeScreen
import com.fsosuke.personamusic.ui.stats.StatsScreen
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors

private object Routes {
    const val PLAYER = "player"
    const val STATS = "stats"
    const val CUSTOMIZE = "customize"
    const val PLAYLIST = "playlist/{id}"
    fun playlist(id: Long) = "playlist/$id"
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

    val showChrome = route != Routes.PLAYER
    val selectedTab = Tab.entries.firstOrNull { it.route == route }
        ?: if (route == Routes.PLAYLIST) Tab.Playlists else null

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

    Box(Modifier.fillMaxSize().background(colors.background)) {
        NavHost(
            navController = nav,
            startDestination = Tab.Home.route,
            enterTransition = { fadeIn(tween(180)) },
            exitTransition = { fadeOut(tween(120)) },
        ) {
            composable(Tab.Home.route) {
                HomeScreen(onOpenSettings = { nav.navigate(Routes.CUSTOMIZE) }, contentPadding = padding)
            }
            composable(Tab.Library.route) { LibraryScreen(contentPadding = padding) }
            composable(Tab.Playlists.route) {
                PlaylistsScreen(onOpen = { nav.navigate(Routes.playlist(it)) }, contentPadding = padding)
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
                    onOpenStats = { nav.navigate(Routes.STATS) },
                    onOpenCustomize = { nav.navigate(Routes.CUSTOMIZE) },
                )
            }
            composable(Routes.STATS) { StatsScreen(onBack = { nav.popBackStack() }, contentPadding = padding) }
            composable(Routes.CUSTOMIZE) { CustomizeScreen(onBack = { nav.popBackStack() }, contentPadding = padding) }
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
