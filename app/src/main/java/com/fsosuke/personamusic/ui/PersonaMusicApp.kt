package com.fsosuke.personamusic.ui

import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import com.fsosuke.personamusic.ui.components.TakeYourHeartSplash
import com.fsosuke.personamusic.ui.components.SlashWipe
import com.fsosuke.personamusic.ui.components.LocalReduceMotion
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fsosuke.personamusic.data.SmartPlaylist
import com.fsosuke.personamusic.data.WrappedPeriod
import com.fsosuke.personamusic.ui.playlist.SmartPlaylistScreen
import com.fsosuke.personamusic.ui.radio.RadioScreen
import com.fsosuke.personamusic.ui.radio.RadioScheduleScreen
import com.fsosuke.personamusic.radio.RadikoStation
import com.fsosuke.personamusic.radio.toJson
import com.fsosuke.personamusic.radio.toStation
import org.json.JSONObject
import com.fsosuke.personamusic.ui.artist.ArtistScreen
import com.fsosuke.personamusic.ui.calendar.CalendarScreen
import com.fsosuke.personamusic.ui.wrapped.WrappedScreen
import java.time.YearMonth
import com.fsosuke.personamusic.ui.confidant.ConfidantsScreen
import com.fsosuke.personamusic.ui.confidant.RankUpOverlay
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
import com.fsosuke.personamusic.ui.settings.HiddenScreen
import com.fsosuke.personamusic.ui.settings.SoundScreen
import com.fsosuke.personamusic.ui.social.SocialStatsScreen
import com.fsosuke.personamusic.ui.social.StatUpOverlay
import com.fsosuke.personamusic.ui.stats.StatsScreen
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors
import com.fsosuke.personamusic.ui.youtube.YtLoginScreen
import com.fsosuke.personamusic.ui.youtube.YtPlaylistScreen

private object Routes {
    const val TABS = "tabs"
    const val PLAYER = "player"
    const val QUEUE = "queue"
    const val DOWNLOADS = "downloads"
    const val CONFIDANTS = "confidants"
    const val HIDDEN = "hidden"
    const val CALENDAR = "calendar"
    const val SOUND = "sound"
    const val SOCIAL = "social"
    const val SMART = "smart/{id}"
    const val RADIO_SCHEDULE = "radio_schedule?station={station}"
    fun radioSchedule(station: RadikoStation) = "radio_schedule?station=" + Uri.encode(station.toJson().toString())
    const val WRAPPED = "wrapped/{period}"
    fun wrapped(period: WrappedPeriod) = "wrapped/" + when (period) {
        is WrappedPeriod.Month -> period.month.toString()
        is WrappedPeriod.Year -> period.year.toString()
    }
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

    val showChrome = route != Routes.PLAYER && route != Routes.YT_LOGIN && route != Routes.QUEUE && route != Routes.WRAPPED
    val reduceMotion by container.settings.reduceMotion.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState { Tab.entries.size }
    val scope = rememberCoroutineScope()
    val selectedTab = if (route == Routes.TABS) Tab.entries[pagerState.targetPage]
        else if (route == Routes.PLAYLIST || route == Routes.YT_PLAYLIST || route == Routes.DOWNLOADS || route == Routes.SMART) Tab.Playlists
        else if (route == Routes.RADIO_SCHEDULE) Tab.Radio else null

    var chromeHeightPx by remember { mutableIntStateOf(0) }
    val chromeHeight = with(LocalDensity.current) { chromeHeightPx.toDp() }
    val padding = PaddingValues(bottom = if (showChrome) chromeHeight else 0.dp)

    fun selectTab(tab: Tab) {
        if (route != Routes.TABS) nav.popBackStack(Routes.TABS, inclusive = false)
        scope.launch {
            if (reduceMotion) pagerState.scrollToPage(tab.ordinal) else pagerState.animateScrollToPage(tab.ordinal)
        }
    }

    val openArtist: (String, String?) -> Unit = { name, id -> nav.navigate(Routes.artist(name, id)) }

    CompositionLocalProvider(LocalOpenArtist provides openArtist, LocalReduceMotion provides reduceMotion) {
    Box(Modifier.fillMaxSize().background(colors.background)) {
        NavHost(
            navController = nav,
            startDestination = Routes.TABS,
            enterTransition = {
                if (reduceMotion) fadeIn(tween(180))
                else if (targetState.destination.route == Routes.PLAYER) slideInVertically(spring(dampingRatio = 0.8f, stiffness = 500f)) { it } + fadeIn(tween(120))
                else slideInHorizontally(tween(260)) { it / 6 } + fadeIn(tween(200))
            },
            exitTransition = {
                if (reduceMotion || initialState.destination.route == Routes.PLAYER) fadeOut(tween(120))
                else slideOutHorizontally(tween(220)) { -it / 10 } + fadeOut(tween(160))
            },
            popEnterTransition = {
                if (reduceMotion) fadeIn(tween(180)) else slideInHorizontally(tween(260)) { -it / 6 } + fadeIn(tween(200))
            },
            popExitTransition = {
                if (reduceMotion) fadeOut(tween(120))
                else if (initialState.destination.route == Routes.PLAYER) slideOutVertically(tween(260)) { it } + fadeOut(tween(220))
                else slideOutHorizontally(tween(220)) { it / 6 } + fadeOut(tween(160))
            },
        ) {
            // The four main areas share one screen and can be swiped between.
            composable(Routes.TABS) {
                MainPager(pagerState, reduceMotion) { tab ->
                    when (tab) {
                        Tab.Home -> HomeScreen(
                            onOpenSettings = { nav.navigate(Routes.CUSTOMIZE) },
                            onOpenYtPlaylist = { nav.navigate(Routes.ytPlaylist(it)) },
                            onSignIn = { nav.navigate(Routes.YT_LOGIN) },
                            onOpenConfidants = { nav.navigate(Routes.CONFIDANTS) },
                            onOpenCalendar = { nav.navigate(Routes.CALENDAR) },
                            onOpenWrapped = { nav.navigate(Routes.wrapped(it)) },
                            onOpenSocial = { nav.navigate(Routes.SOCIAL) },
                            contentPadding = padding,
                        )
                        Tab.Library -> LibraryScreen(contentPadding = padding)
                        Tab.Playlists -> PlaylistsScreen(
                            onOpen = { nav.navigate(Routes.playlist(it)) },
                            onOpenYt = { nav.navigate(Routes.ytPlaylist(it)) },
                            onOpenDownloads = { nav.navigate(Routes.DOWNLOADS) },
                            onOpenSmart = { nav.navigate("smart/$it") },
                            contentPadding = padding,
                        )
                        Tab.Radio -> RadioScreen(contentPadding = padding, onOpenSchedule = { nav.navigate(Routes.radioSchedule(it)) })
                    }
                }
            }
            composable(Routes.RADIO_SCHEDULE, arguments = listOf(navArgument("station") { type = NavType.StringType })) { entry ->
                val station = entry.arguments?.getString("station")?.let { runCatching { JSONObject(it).toStation() }.getOrNull() }
                if (station != null) RadioScheduleScreen(station, onBack = { nav.popBackStack() }, contentPadding = padding)
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
            composable(Routes.STATS) {
                StatsScreen(
                    onBack = { nav.popBackStack() },
                    onOpenConfidants = { nav.navigate(Routes.CONFIDANTS) },
                    onOpenCalendar = { nav.navigate(Routes.CALENDAR) },
                    onOpenSocial = { nav.navigate(Routes.SOCIAL) },
                    contentPadding = padding,
                )
            }
            composable(Routes.CALENDAR) {
                CalendarScreen(
                    onBack = { nav.popBackStack() },
                    onOpenWrapped = { nav.navigate(Routes.wrapped(it)) },
                    contentPadding = padding,
                )
            }
            composable(Routes.WRAPPED, arguments = listOf(navArgument("period") { type = NavType.StringType })) { entry ->
                val raw = entry.arguments?.getString("period").orEmpty()
                val period = if (raw.length == 4) WrappedPeriod.Year(raw.toInt()) else WrappedPeriod.Month(YearMonth.parse(raw))
                WrappedScreen(period, onClose = { nav.popBackStack() })
            }
            composable(Routes.SMART, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val smart = SmartPlaylist.byId(entry.arguments?.getString("id").orEmpty()) ?: SmartPlaylist.OnRepeat
                SmartPlaylistScreen(smart, onBack = { nav.popBackStack() }, contentPadding = padding)
            }
            composable(Routes.SOCIAL) { SocialStatsScreen(onBack = { nav.popBackStack() }, contentPadding = padding) }
            composable(Routes.SOUND) { SoundScreen(onBack = { nav.popBackStack() }, contentPadding = padding) }
            composable(Routes.HIDDEN) { HiddenScreen(onBack = { nav.popBackStack() }, contentPadding = padding) }
            composable(Routes.CONFIDANTS) { ConfidantsScreen(onBack = { nav.popBackStack() }, contentPadding = padding) }
            composable(Routes.CUSTOMIZE) { CustomizeScreen(
                    onBack = { nav.popBackStack() },
                    onSignIn = { nav.navigate(Routes.YT_LOGIN) },
                    onOpenHidden = { nav.navigate(Routes.HIDDEN) },
                    onOpenSound = { nav.navigate(Routes.SOUND) },
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

        // Slash wipe between screens (not for the player, which slides up on its own).
        SlashWipe(trigger = route?.takeIf { it != Routes.PLAYER && it != Routes.QUEUE })
        TakeYourHeartSplash()
        RankUpOverlay()
        StatUpOverlay()
    }
    }
}

/**
 * Home, Library, Playlists and Radio side by side: swipe left/right to switch. Pages tilt and
 * shrink a little while moving, and a red slash runs along the seam.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainPager(state: PagerState, reduceMotion: Boolean, page: @Composable (Tab) -> Unit) {
    val colors = LocalPersonaColors.current
    val scope = rememberCoroutineScope()
    // Back on another area returns to Home first, like the bottom bar used to.
    BackHandler(enabled = state.currentPage != 0) { scope.launch { state.animateScrollToPage(0) } }
    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = state,
            beyondViewportPageCount = 1,
            key = { Tab.entries[it].route },
            modifier = Modifier.fillMaxSize(),
        ) { index ->
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (reduceMotion) return@graphicsLayer
                        val offset = (state.currentPage - index) + state.currentPageOffsetFraction
                        val amount = offset.coerceIn(-1f, 1f)
                        rotationZ = amount * 4f
                        val s = 1f - 0.08f * kotlin.math.abs(amount)
                        scaleX = s
                        scaleY = s
                    },
            ) { page(Tab.entries[index]) }
        }
        if (!reduceMotion) {
            Canvas(Modifier.fillMaxSize()) {
                val fraction = state.currentPageOffsetFraction
                if (kotlin.math.abs(fraction) < 0.01f) return@Canvas
                val w = size.width
                val h = size.height
                // Where the current page ends and the next begins.
                val seam = if (fraction > 0) w * (1f - fraction) else w * -fraction
                val band = w * 0.07f
                fun slash(x: Float, width: Float) = Path().apply {
                    moveTo(x + w * 0.12f, 0f)
                    lineTo(x + w * 0.12f + width, 0f)
                    lineTo(x - w * 0.12f + width, h)
                    lineTo(x - w * 0.12f, h)
                    close()
                }
                drawPath(slash(seam - band / 2, band), colors.accent)
                drawPath(slash(seam + band / 2, band * 0.25f), Color.White)
                drawPath(slash(seam - band / 2 - band * 0.3f, band * 0.3f), Color.Black)
            }
        }
    }
}
