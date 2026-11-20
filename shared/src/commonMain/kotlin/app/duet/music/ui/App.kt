package app.duet.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duet.music.DuetGraph
import kotlinx.coroutines.launch
import app.duet.music.extension.SourceRegistry
import app.duet.music.player.PlaybackStatus
import app.duet.music.player.PlayerState
import app.duet.music.ui.components.MiniPlayer
import app.duet.music.ui.components.MiniPlayerUi
import app.duet.music.ui.components.Tab
import app.duet.music.ui.components.TabBar
import app.duet.music.ui.home.HomeScreen
import app.duet.music.ui.home.HomeUi
import app.duet.music.ui.home.greetingFor
import app.duet.music.ui.library.LibraryScreen
import app.duet.music.ui.library.LibraryUi
import app.duet.music.ui.player.LyricsScreen
import app.duet.music.ui.player.NowPlayingScreen
import app.duet.music.ui.player.SaveUi
import app.duet.music.settings.Flag
import app.duet.music.settings.HomeSection
import app.duet.music.extension.Capability
import app.duet.music.domain.Track
import app.duet.music.ui.player.QueueSheet
import app.duet.music.ui.search.SearchScreen
import app.duet.music.ui.search.CollectionScreen
import app.duet.music.ui.settings.ProfileSheet
import app.duet.music.ui.settings.SettingsScreen
import app.duet.music.ui.settings.SourceScreen
import app.duet.music.ui.theme.DuetColors
import app.duet.music.ui.theme.duetGround
import app.duet.music.ui.theme.DuetIcons
import app.duet.music.ui.theme.DuetDimens
import app.duet.music.ui.theme.DuetTheme

/** Full-screen pages drawn over the tabs, newest last. */
sealed interface Page {
    data object NowPlaying : Page
    data object Lyrics : Page
    data class CollectionPage(val collection: app.duet.music.domain.Collection) : Page
    /** Downloaded or Cached songs on this device. */
    data class LocalPage(val cached: Boolean) : Page
    data class PlaylistPage(val id: String) : Page
    data object Liked : Page
    data object History : Page
    data object Stats : Page
    data object Settings : Page
    /** One group of settings, opened from the Settings menu. */
    data class SettingsGroup(val group: app.duet.music.ui.settings.SettingsGroup) : Page
    data object Sound : Page
    data object Car : Page
    data class Source(val id: String) : Page
    /** The guided style editor; [editId] is a custom style to change, null for a new one. */
    data class StyleEditor(val editId: String?) : Page
    data object ThemeShop : Page
    data class SharedStyle(val id: String) : Page
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
/**
 * The whole app. [desktop] swaps the phone's bottom tabs and mini player for a left sidebar and a player bar along the
 * bottom (computers); everything in between, the screens and pages, is the same.
 */
fun App(graph: DuetGraph, initialTab: Tab = Tab.Home, initialPages: List<Page> = emptyList(), desktop: Boolean = false, commands: app.duet.music.ui.desktop.AppCommands? = null) {
    // v0.21: the owner's Look Studio look replaces Marquee (D63); move everyone on it once, with its white highlight.
    androidx.compose.runtime.remember { app.duet.music.ui.theme.DuetStyles.first().let { graph.settings.resetLookOnce(5, it.id, it.accent) } }
    val accent by graph.settings.accent.collectAsState()
    val customLooks by graph.settings.customLooks.collectAsState()
    val styleId by graph.settings.style.collectAsState()
    val lightMode by graph.settings.lightMode.collectAsState()
    val style = remember(styleId, customLooks, lightMode) {
        app.duet.music.ui.theme.styleById(styleId, customLooks).let { if (lightMode) it.light() else it }
    }
    val menuTrack = remember { mutableStateOf<Track?>(null) }
    DuetTheme(accent = Color(accent), style = style) {
      // The UI follows the window: a small window gets a small UI (60%), a big or ultrawide one a larger one (up to 120%).
      val baseDensity = androidx.compose.ui.platform.LocalDensity.current
      val windowScale = if (desktop) androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.let { sz ->
          // Both sides count: an ultrawide screen is wide but not tall, so the height keeps the UI from growing past what fits.
          minOf(sz.width / baseDensity.density / 1700f, sz.height / baseDensity.density / 900f).coerceIn(0.6f, 1.2f)
      } else 1f
      androidx.compose.runtime.CompositionLocalProvider(app.duet.music.ui.components.LocalTrackMenu provides { t -> menuTrack.value = t }, app.duet.music.ui.desktop.LocalDesktop provides desktop,
          // A computer's window is read from an arm's length: the whole UI is drawn a little smaller than on a phone.
          androidx.compose.ui.platform.LocalDensity provides baseDensity.let {
              if (desktop) androidx.compose.ui.unit.Density(it.density * windowScale, it.fontScale) else it
          },
          // The Mac title bar's height is fixed in screen points, so it is divided by the UI's scale.
          app.duet.music.ui.desktop.LocalTitleBarInset provides (app.duet.music.ui.desktop.LocalTitleBarInset.current.value / windowScale).dp) {
        var tab by rememberSaveable { mutableStateOf(initialTab) }
        val pages = remember { mutableStateListOf<Page>().apply { addAll(initialPages) } }
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        // A song's artist line and "Go to artist": the artist's page on the song's own service (or another).
        val openArtist: (Track) -> Unit = { t -> scope.launch { app.duet.music.search.findArtist(graph.sources, t)?.let { pages += Page.CollectionPage(it) } } }
        var showQueue by remember { mutableStateOf(false) }
        // Search tab: start page (history of songs) or the search screen (typing, past searches, results).
        var searchActive by rememberSaveable { mutableStateOf(false) }
        BackHandler(enabled = pages.isEmpty() && tab == Tab.Search && searchActive) { searchActive = false }
        var showProfile by remember { mutableStateOf(false) }
        val player by graph.player.state.collectAsState()
        val mini = player.toMiniPlayerUi(graph.sources)
        // Leave room under scrolling content for the mini player and tab bar.
        val bottomSpace = if (desktop) PaddingValues(bottom = 24.dp) else PaddingValues(bottom = (DuetDimens.TabBarHeight + DuetDimens.MiniPlayerHeight + 24).dp)

        // Pages on their way out (they animate, then leave `pages`), and how far each is shown (0..1).
        val closingPages = remember { mutableStateListOf<Page>() }
        val shownBy = remember { androidx.compose.runtime.mutableStateMapOf<Page, androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>>() }
        // On a computer the player bar goes away while the full player (or the lyrics) is open: it would repeat the same controls.
        val playerOpen = pages.any { (it == Page.NowPlaying || it == Page.Lyrics) && it !in closingPages }
        val barHeight by androidx.compose.animation.core.animateDpAsState(
            if (desktop && !playerOpen) app.duet.music.ui.desktop.desktopBarHeight() else 0.dp, label = "playerBar",
        )
        // System back slides the top page away; on the base tabs it leaves the app as usual.
        BackHandler(enabled = pages.isNotEmpty()) { pages.lastOrNull { it !in closingPages }?.let { closingPages += it } ?: pages.removeAt(pages.lastIndex) }
        // Close Now Playing if the queue empties under it.
        val nothingQueued = player.track == null
        LaunchedEffect(nothingQueued) { if (nothingQueued) { pages.remove(Page.NowPlaying); pages.remove(Page.Lyrics) } }

        // Every screen's ground takes the playing song's colour.
        val groundTint = player.track?.let { app.duet.music.ui.player.rememberArtColor(it.artworkUrl, fallback = DuetColors.Ground) }
        val animatedTint = groundTint?.let { androidx.compose.animation.animateColorAsState(it, androidx.compose.animation.core.tween(700), label = "ground").value }
        androidx.compose.runtime.CompositionLocalProvider(app.duet.music.ui.theme.LocalGroundTint provides animatedTint) {
        // The Mac menu bar's commands (Search, Settings, Now Playing, Lyrics).
        if (commands != null) LaunchedEffect(commands) {
            commands.flow.collect { c ->
                when (c) {
                    app.duet.music.ui.desktop.AppCommand.Search -> { pages.toList().forEach { p -> if (p !in closingPages) closingPages += p }; tab = Tab.Search; searchActive = true }
                    app.duet.music.ui.desktop.AppCommand.Settings -> if (Page.Settings !in pages) pages += Page.Settings
                    app.duet.music.ui.desktop.AppCommand.NowPlaying -> if (player.track != null) { if (Page.NowPlaying in pages) closingPages += Page.NowPlaying else pages += Page.NowPlaying }
                    app.duet.music.ui.desktop.AppCommand.Lyrics -> if (player.track != null && Page.Lyrics !in pages) pages += Page.Lyrics
                }
            }
        }
        // Computer: Escape closes the top page (player, lyrics, settings...), like the back gesture on a phone.
        val rootFocus = remember { androidx.compose.ui.focus.FocusRequester() }
        if (desktop) LaunchedEffect(Unit) { runCatching { rootFocus.requestFocus() } }
        Box(Modifier.fillMaxSize().duetGround().then(if (!desktop) Modifier else Modifier
            .onPreviewKeyEvent { e ->
                val top = pages.lastOrNull { it !in closingPages }
                if (e.key == androidx.compose.ui.input.key.Key.Escape && e.type == androidx.compose.ui.input.key.KeyEventType.KeyUp && top != null) { closingPages += top; true } else false
            }
            // Space plays and pauses. It is handled on the way back up (not the way down), so a text field that is being typed in
            // takes the space first and this never sees it: typing is safe.
            .onKeyEvent { e ->
                if (e.key == androidx.compose.ui.input.key.Key.Spacebar && e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && player.track != null) { graph.player.togglePlayPause(); true } else false
            }.focusRequester(rootFocus).focusable())) {
          // On desktop the sidebar and player bar take the left and bottom edges; the screens live in what is left.
          Box(Modifier.fillMaxSize().then(if (desktop) Modifier.padding(start = app.duet.music.ui.desktop.desktopSidebarWidth(), top = app.duet.music.ui.desktop.LocalTitleBarInset.current, bottom = barHeight) else Modifier)) {
            // Hidden under a full-screen page: keep it out of screen readers (it stays composed so
            // scroll positions and search text survive).
            // Depth: under the player (or lyrics, car mode) the app sinks back a little and dims.
            val sheetShown = pages.lastOrNull { it == Page.NowPlaying || it == Page.Lyrics || it == Page.Car }?.let { shownBy[it]?.value } ?: 0f
            Box(Modifier.fillMaxSize().graphicsLayer {
                // Desktop: the sidebar must not shrink and dim while the player opens.
                val k = if (desktop) 1f else 1f - 0.06f * sheetShown
                scaleX = k; scaleY = k; alpha = if (desktop) 1f else 1f - 0.35f * sheetShown
            }.then(if (pages.isNotEmpty()) Modifier.clearAndSetSemantics {} else Modifier)) {
              // Tabs fade into each other.
              androidx.compose.animation.Crossfade(tab, animationSpec = androidx.compose.animation.core.tween(220), label = "tab") { shown ->
              app.duet.music.ui.desktop.DesktopFit(full = false, ground = false) {
              when (shown) {
                  Tab.Home -> {
                      var homeOnly by rememberSaveable { mutableStateOf<String?>(null) }
                      val keep: (String) -> Boolean = { homeOnly == null || it == homeOnly }
                      val recent = graph.history.recent.collectAsState().value.filter { keep(it.sourceId) }
                      val hidden by graph.settings.hiddenSections.collectAsState()
                      val quickPicks = graph.home.quickPicks.collectAsState().value.filter { keep(it.sourceId) }
                      val shelves = graph.home.visibleShelves(graph.home.shelves.collectAsState().value, quickPicks)
                          .filter { keep(it.sourceId) && "${it.sourceId}:${it.title}" !in hidden }
                      val saved = graph.downloads?.saved?.collectAsState()?.value.orEmpty().filter { keep(it.sourceId) }
                      HomeScreen(
                          ui = HomeUi(
                              greetingFor(currentHour()), recent, saved, shelves,
                              services = graph.sources.music.filter { it.account?.signedIn?.collectAsState()?.value /* recompose on sign-in */; it.playableNow }.map { Triple(it.manifest.id, it.shortName, it.color) },
                              only = homeOnly,
                              quickPicks = quickPicks,
                              showQuickPicks = HomeSection.QuickPicks.id !in hidden,
                              showJumpBackIn = HomeSection.JumpBackIn.id !in hidden,
                              showSaved = HomeSection.Saved.id !in hidden,
                              showRecent = HomeSection.Recent.id !in hidden,
                              radioSeeds = graph.home.radioSeeds(recent),
                              showRadio = HomeSection.Radio.id !in hidden,
                              userName = graph.sources.music.firstNotNullOfOrNull { p ->
                                  p.account?.status?.collectAsState()?.value?.takeIf { it.startsWith("Signed in as ") }?.removePrefix("Signed in as ")
                              },
                              shortcuts = listOfNotNull(graph.likes.list.collectAsState().value.takeIf { it.isNotEmpty() }?.let { l ->
                                  app.duet.music.ui.home.HomeShortcut("liked", "Liked songs", l.first().artworkUrl) { pages += Page.Liked }
                              }) + graph.playlists.list.collectAsState().value.take(4).map { p ->
                                  app.duet.music.ui.home.HomeShortcut("pl:" + p.id, p.name, p.tracks.firstOrNull()?.artworkUrl) { pages += Page.PlaylistPage(p.id) }
                              } + recent.take(6).mapIndexed { i, t ->
                                  app.duet.music.ui.home.HomeShortcut(t.key, t.title, t.artworkUrl) { graph.player.playAll(recent, i) }
                              },
                          ),
                          onStartRadio = { seed -> scope.launch { graph.player.playAll(graph.home.radioFor(seed), 0) } },
                          onCarMode = { pages += Page.Car },
                          onService = { homeOnly = it },
                          contentPadding = bottomSpace,
                          onProfile = { showProfile = true },
                          onSettings = { pages += Page.Settings },
                          onPlay = graph.player::playAll,
                          onOpenCollection = { pages += Page.CollectionPage(it) },
                          onSearch = { tab = Tab.Search; searchActive = true },
                      )
                  }
                  Tab.Search -> SearchScreen(
                      model = graph.search,
                      sources = graph.sources,
                      playingKey = player.track?.key,
                      onPlay = graph.player::playAll,
                      onAddToQueue = graph.player::addToQueue,
                      contentPadding = bottomSpace,
                      onOpenCollection = { pages += Page.CollectionPage(it) },
                      recent = graph.history.recent.collectAsState().value,
                      history = graph.searchHistory.queries.collectAsState().value,
                      onHistoryRemove = graph.searchHistory::remove,
                      onHistoryClear = graph.searchHistory::clear,
                      onSearched = graph.searchHistory::add,
                      active = searchActive,
                      onActive = { searchActive = it },
                  )
                  Tab.Radio -> (graph.sources[app.duet.music.sources.radio.RadioProvider.ID] as? app.duet.music.sources.radio.RadioProvider)?.let {
                      app.duet.music.ui.radio.RadioScreen(it, graph.favouriteStations, player.track?.key, graph.player::playAll, bottomSpace)
                  }
                  Tab.Library -> {
                      var libOnly by rememberSaveable { mutableStateOf<String?>(null) }
                      val synced = graph.library.items.collectAsState().value.filter { libOnly == null || it.sourceId == libOnly }
                      val syncing by graph.library.syncing.collectAsState()
                      val anySignedIn = graph.sources.providers.mapNotNull { it.account }.map { it.signedIn.collectAsState().value }.any { it }
                      val downloaded = graph.downloads?.saved?.collectAsState()?.value.orEmpty()
                      val cachedCount = cachedSongs(graph).size
                      LibraryScreen(
                          ui = LibraryUi(
                              downloadedCount = downloaded.size, cachedCount = cachedCount, synced = synced, syncing = syncing,
                              anySignedIn = anySignedIn,
                              services = graph.sources.music.filter { it.account?.signedIn?.collectAsState()?.value /* recompose on sign-in */; it.playableNow }.map { Triple(it.manifest.id, it.shortName, it.color) },
                              only = libOnly, error = graph.downloads?.lastError?.collectAsState()?.value,
                              hasHistory = graph.history.recent.collectAsState().value.isNotEmpty(),
                          ),
                          contentPadding = bottomSpace,
                          sourceName = { id -> graph.sources[id].let { (it?.shortName ?: id) to (it?.color ?: 0xFF9AA0B4) } },
                          onOpenDownloaded = { pages += Page.LocalPage(cached = false) },
                          onOpenCached = { pages += Page.LocalPage(cached = true) },
                          onOpenHistory = { pages += Page.History },
                          onOpenStats = { pages += Page.Stats },
                          likedCount = graph.likes.list.collectAsState().value.size,
                          onOpenLiked = { pages += Page.Liked },
                          playlists = graph.playlists.list.collectAsState().value,
                          onOpenPlaylist = { pages += Page.PlaylistPage(it) },
                          onNewPlaylist = { name -> pages += Page.PlaylistPage(graph.playlists.create(name).id) },
                          onDeletePlaylist = graph.playlists::delete,
                          canRemove = { c -> graph.sources[c.sourceId]?.canRemoveFromLibrary(c) == true },
                          onRemove = graph.library::remove,
                          onOpenCollection = { pages += Page.CollectionPage(it) },
                          onService = { libOnly = it },
                          onSync = graph.library::refresh,
                          onConnectAccounts = { showProfile = true },
                      )
                  }
              }
              }
              }

              // Under a page, the mini player and tabs are covered, so a screen reader must not read them either.
              if (!desktop) Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().then(if (pages.isNotEmpty()) Modifier.clearAndSetSemantics {} else Modifier)) {
                  // The ground's own bottom colour (a deep or pale shade of the song's), not one fixed colour.
                  val bottom = app.duet.music.ui.theme.groundBottom()
                  // Fade content out behind the floating controls (design: bottom gradient).
                  Box(
                      Modifier.fillMaxWidth().height(24.dp)
                          .background(Brush.verticalGradient(listOf(bottom.copy(alpha = 0f), bottom))),
                  )
                  // The mini player slides up when music starts and down when the queue ends.
                  var lastMini by remember { mutableStateOf(mini) }
                  if (mini != null) lastMini = mini
                  androidx.compose.animation.AnimatedVisibility(
                      visible = mini != null,
                      enter = androidx.compose.animation.slideInVertically { it } + androidx.compose.animation.fadeIn(),
                      exit = androidx.compose.animation.slideOutVertically { it } + androidx.compose.animation.fadeOut(),
                  ) {
                      lastMini?.let { m ->
                          // A slim bar sits flush with the edges; cards float with a margin.
                          val flush = app.duet.music.ui.theme.LocalStyle.current.miniSize == app.duet.music.ui.theme.MiniSize.Slim
                          Box(Modifier.fillMaxWidth().background(bottom).padding(start = if (flush) 0.dp else 8.dp, end = if (flush) 0.dp else 8.dp, bottom = if (flush) 0.dp else 6.dp)) {
                              MiniPlayer(m, onTogglePlay = graph.player::togglePlayPause, onOpen = { pages += Page.NowPlaying }, onPrevious = graph.player::previous, onNext = graph.player::next,
                                  liked = player.track?.takeUnless { Capability.LiveRadio in (graph.sources[it.sourceId]?.manifest?.capabilities ?: emptySet()) }
                                      ?.let { t -> graph.likes.list.collectAsState().value.any { it.key == t.key } },
                                  onToggleLike = { player.track?.let(graph::toggleLike) })
                          }
                      }
                  }
                  TabBar(tab, onSelect = { tab = it }, modifier = Modifier.background(bottom).navigationBarsPadding())
              }
            }

              pages.forEach { page ->
                  // Pages animate in and out: the player and lyrics slide up from the bottom, the rest in from the side.
                  val shownAmount = remember(page) { androidx.compose.animation.core.Animatable(0f).also { shownBy[page] = it } }
                  val closing = page in closingPages
                  LaunchedEffect(page, closing) {
                      // In with a soft spring, out quickly.
                      if (closing) shownAmount.animateTo(0f, androidx.compose.animation.core.tween(200, easing = androidx.compose.animation.core.FastOutLinearInEasing))
                      else shownAmount.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.86f, stiffness = 380f))
                      if (closing) { pages.remove(page); closingPages.remove(page); shownBy.remove(page) }
                  }
                  val close: () -> Unit = { if (page !in closingPages) closingPages += page }
                  val fromBottom = page == Page.NowPlaying || page == Page.Lyrics || page == Page.Car
                  val pageScope = androidx.compose.runtime.rememberCoroutineScope()
                  var pageHeight by remember(page) { mutableStateOf(1f) }
                  // Bottom pages follow the finger down once their content is scrolled to the top, and close if let go low enough or flung.
                  val dragToClose = remember(page) {
                      object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                          override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                              if (available.y >= 0f || shownAmount.value >= 1f) return androidx.compose.ui.geometry.Offset.Zero
                              pageScope.launch { shownAmount.snapTo((shownAmount.value - available.y / pageHeight).coerceAtMost(1f)) }
                              return androidx.compose.ui.geometry.Offset(0f, available.y)
                          }
                          override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                              if (available.y <= 0f || source != androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput) return androidx.compose.ui.geometry.Offset.Zero
                              pageScope.launch { shownAmount.snapTo((shownAmount.value - available.y / pageHeight).coerceAtLeast(0f)) }
                              return androidx.compose.ui.geometry.Offset(0f, available.y)
                          }
                          override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                              if (shownAmount.value >= 1f) return androidx.compose.ui.unit.Velocity.Zero
                              if (shownAmount.value < 0.75f || available.y > 1800f) close()
                              else shownAmount.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 500f))
                              return available
                          }
                      }
                  }
                  // pointerInput stops taps on empty parts of the page from reaching what is underneath.
                  // A page with another page on top of it is covered: hidden from screen readers, like the base screen.
                  Box(Modifier.fillMaxSize().onSizeChanged { pageHeight = it.height.toFloat().coerceAtLeast(1f) }
                      .then(if (page != pages.last()) Modifier.clearAndSetSemantics {} else Modifier)
                      .then(if (fromBottom) Modifier.nestedScroll(dragToClose) else Modifier).pointerInput(Unit) {}.graphicsLayer {
                      val t = 1f - shownAmount.value
                      if (fromBottom) translationY = t * size.height else { translationX = t * size.width * 0.3f; alpha = shownAmount.value }
                  }) { app.duet.music.ui.desktop.DesktopFit(full = page == Page.NowPlaying || page == Page.Lyrics || page == Page.Car, ground = true) { when (page) {
                      Page.NowPlaying -> app.duet.music.ui.theme.OnArtTheme { NowPlayingScreen(
                          player, graph.sources, graph.player, onClose = close, onOpenQueue = { showQueue = true }, onOpenLyrics = { pages += Page.Lyrics },
                          showLyricsButton = graph.settings.flag(Flag.LyricsButton).collectAsState().value,
                          showSourceLine = graph.settings.flag(Flag.SourceLine).collectAsState().value,
                          save = saveButton(graph, player.track),
                          liked = player.track?.takeUnless { Capability.LiveRadio in (graph.sources[it.sourceId]?.manifest?.capabilities ?: emptySet()) }
                              ?.let { t -> graph.likes.list.collectAsState().value.any { it.key == t.key } },
                          onToggleLike = { player.track?.let(graph::toggleLike) },
                          onOpenArtist = player.track?.takeUnless { Capability.LiveRadio in (graph.sources[it.sourceId]?.manifest?.capabilities ?: emptySet()) }
                              ?.let { t -> { openArtist(t) } },
                          sleepMinutes = graph.settings.sleepMinutes.collectAsState().value,
                          onSleep = graph.settings::setSleepMinutes,
                      ) }
                      Page.Sound -> app.duet.music.ui.settings.SoundScreen(graph.settings.sound, onBack = close)
                      Page.Car -> {
                          val recent by graph.history.recent.collectAsState()
                          val quick by graph.home.quickPicks.collectAsState()
                          var radio by remember { mutableStateOf<List<app.duet.music.domain.Track>>(emptyList()) }
                          LaunchedEffect(recent.firstOrNull()?.key) { recent.firstOrNull()?.let { radio = graph.home.radioFor(it) } }
                          val downloaded = graph.downloads?.saved?.collectAsState()?.value.orEmpty()
                          app.duet.music.ui.car.CarModeScreen(
                              player = player,
                              lists = listOfNotNull(
                                  app.duet.music.ui.car.CarList("Stations", DuetIcons.Radio, graph.favouriteStations.list.collectAsState().value)
                                      .takeIf { it.songs.isNotEmpty() },
                                  app.duet.music.ui.car.CarList("Quick picks", DuetIcons.Library, quick),
                                  app.duet.music.ui.car.CarList("Mix", DuetIcons.Radio, radio)
                                      .takeIf { HomeSection.Radio.id !in graph.settings.hiddenSections.collectAsState().value },
                                  app.duet.music.ui.car.CarList("Recent", DuetIcons.Repeat, recent),
                                  app.duet.music.ui.car.CarList("Downloaded", DuetIcons.Download, downloaded),
                              ),
                              onPlay = graph.player::playAll,
                              onTogglePlay = graph.player::togglePlayPause,
                              onNext = graph.player::next,
                              onPrevious = graph.player::previous,
                              onExit = close,
                          )
                      }
                      is Page.LocalPage -> {
                          val list = if (page.cached) cachedSongs(graph) else graph.downloads?.saved?.collectAsState()?.value.orEmpty()
                          val c = app.duet.music.domain.Collection("local", if (page.cached) "cached" else "downloaded", app.duet.music.domain.SearchKind.Playlists,
                              if (page.cached) "Played offline" else "Downloaded", if (page.cached) "Played before, kept on this phone" else "Saved on this phone")
                          CollectionScreen(c, graph.sources, player.track?.key, onPlay = graph.player::playAll, onBack = close, preloaded = list,
                              onRemove = graph.downloads?.takeIf { !page.cached }?.let { d -> { t -> d.delete(t.key) } })
                      }
                      is Page.PlaylistPage -> app.duet.music.ui.library.PlaylistScreen(page.id, graph.playlists, graph.sources, player.track?.key,
                          onPlay = graph.player::playAll, onBack = close)
                      Page.Liked -> CollectionScreen(
                          app.duet.music.domain.Collection("local", "liked", app.duet.music.domain.SearchKind.Playlists, "Liked songs", "Songs you liked with the heart"),
                          graph.sources, player.track?.key, onPlay = graph.player::playAll, onBack = close,
                          preloaded = graph.likes.list.collectAsState().value, onRemove = graph::toggleLike, askBeforeRemove = false,
                      )
                      Page.History -> {
                          val list = graph.history.recent.collectAsState().value
                          CollectionScreen(
                              app.duet.music.domain.Collection("local", "history", app.duet.music.domain.SearchKind.Playlists, "History", "Played on this phone"),
                              graph.sources, player.track?.key, onPlay = graph.player::playAll, onBack = close, preloaded = list,
                          )
                      }
                      Page.Stats -> {
                          graph.history.counts.collectAsState().value // recompose when a play is counted
                          app.duet.music.ui.library.StatsScreen(
                              songs = graph.history.topSongs(20, skipSource = app.duet.music.sources.radio.RadioProvider.ID),
                              artists = graph.history.topArtists(10, skipSource = app.duet.music.sources.radio.RadioProvider.ID),
                              playingKey = player.track?.key, onPlay = graph.player::playAll, onBack = close,
                          )
                      }
                      is Page.CollectionPage -> if (page.collection.kind == app.duet.music.domain.SearchKind.Artists)
                          app.duet.music.ui.search.ArtistScreen(page.collection, graph.sources, player.track?.key, onPlay = graph.player::playAll,
                              onOpen = { pages += Page.CollectionPage(it) }, onBack = close)
                      else CollectionScreen(page.collection, graph.sources, player.track?.key, onPlay = graph.player::playAll, onBack = close,
                          onCopy = { tracks -> val id = graph.playlists.create(page.collection.title).id; graph.playlists.addAll(id, tracks); pages += Page.PlaylistPage(id) },
                          onEdited = graph.library::refresh,
                          onRemoveFromLibrary = graph.library::remove)
                      Page.Lyrics -> app.duet.music.ui.theme.OnArtTheme { LyricsScreen(player, graph.lyrics, graph.player, onClose = close) }
                      Page.ThemeShop -> app.duet.music.ui.settings.ThemeShopScreen(graph.shop, onBack = close,
                          onSignIn = { pages += Page.Source(app.duet.music.sources.ytmusic.YouTubeMusicProvider.ID) },
                          onOpen = { pages += Page.SharedStyle(it.id) })
                      is Page.SharedStyle -> app.duet.music.ui.settings.SharedStyleScreen(graph.shop, page.id, onBack = close, onTried = close)
                      is Page.StyleEditor -> app.duet.music.ui.settings.StyleEditor(
                          graph.settings, graph.settings.customLooks.value.firstOrNull { it.id == page.editId }, onClose = close,
                      )
                      Page.Settings, is Page.SettingsGroup -> SettingsScreen(
                          graph.settings, graph.sources, graph.appVersion,
                          onEditStyle = { pages += Page.StyleEditor(it) },
                          shop = graph.shop, onOpenShop = { pages += Page.ThemeShop },
                          group = (page as? Page.SettingsGroup)?.group,
                          onOpenGroup = { pages += Page.SettingsGroup(it) },
                          onBack = close, onOpenSource = { pages += Page.Source(it) },
                          downloads = graph.downloads,
                          cache = graph.cache,
                          shelves = graph.home.shelves.collectAsState().value,
                          onOpenSound = { pages += Page.Sound },
                          onOpenCarMode = { pages += Page.Car },
                          updates = graph.updates,
                          releases = graph.releases,
                          onShowTutorial = { pages.clear(); graph.settings.setFlag(Flag.TutorialSeen, false) },
                      )
                      is Page.Source -> graph.sources[page.id]?.let { SourceScreen(it, graph.settings, onBack = close) }
                  } } }
              }
          }
          if (desktop) {
              app.duet.music.ui.desktop.DesktopSidebar(
                  tab,
                  onTab = { t -> tab = t; pages.toList().forEach { p -> if (p !in closingPages) closingPages += p } },
                  onAccounts = { showProfile = true },
                  onSettings = { if (Page.Settings !in pages) pages += Page.Settings },
                  modifier = Modifier.align(Alignment.TopStart).padding(bottom = barHeight),
              )
              if (!playerOpen) app.duet.music.ui.desktop.DesktopPlayerBar(
                  player, app.duet.music.ui.player.PlayerControls.of(graph.player),
                  liked = player.track?.takeUnless { Capability.LiveRadio in (graph.sources[it.sourceId]?.manifest?.capabilities ?: emptySet()) }
                      ?.let { t -> graph.likes.list.collectAsState().value.any { it.key == t.key } },
                  onToggleLike = { player.track?.let(graph::toggleLike) },
                  onOpenPlayer = { if (Page.NowPlaying !in pages) pages += Page.NowPlaying },
                  onOpenQueue = { showQueue = true },
                  onOpenLyrics = { if (Page.Lyrics !in pages) pages += Page.Lyrics },
                  modifier = Modifier.align(Alignment.BottomCenter),
              )
          }
        }
        }

        var savingQueue by remember { mutableStateOf(false) }
        if (showQueue) QueueSheet(player, graph.sources, graph.player, onDismiss = { showQueue = false }, onSaveAsPlaylist = { savingQueue = true })
        if (savingQueue) NewPlaylistDialog(onCreate = { name ->
            savingQueue = false; showQueue = false
            val p = graph.playlists.create(name)
            graph.playlists.addAll(p.id, player.queue.map { it.track })
            pages += Page.PlaylistPage(p.id)
        }, onDismiss = { savingQueue = false })
        if (showProfile) {
            ProfileSheet(
                graph.sources,
                onOpenSettings = { showProfile = false; pages += Page.Settings },
                onOpenSource = { showProfile = false; pages += Page.Source(it) },
                onDismiss = { showProfile = false },
            )
        }
        // First launch: the skippable tour, above everything else.
        if (!graph.settings.flag(Flag.TutorialSeen).collectAsState().value) app.duet.music.ui.components.Tutorial(
            onDone = { graph.settings.setFlag(Flag.TutorialSeen, true) },
            onConnect = { showProfile = true },
        ) else graph.updates?.let { UpdatePrompt(it) }
        menuTrack.value?.let { t -> TrackMenu(graph, t, onDismiss = { menuTrack.value = null }, onOpenArtist = openArtist) }
      }
    }
}

/** The song menu: long press (or ⋯) on any song. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TrackMenu(graph: DuetGraph, t: Track, onDismiss: () -> Unit, onOpenArtist: (Track) -> Unit) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val downloads = graph.downloads
    val saved = downloads?.saved?.collectAsState()?.value.orEmpty().any { it.key == t.key }
    val live = Capability.LiveRadio in (graph.sources[t.sourceId]?.manifest?.capabilities ?: emptySet())
    var askRemove by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    if (picking) AddToPlaylistSheet(graph, t, onDismiss = { picking = false; onDismiss() })
    else if (askRemove) app.duet.music.ui.components.RemoveDownloadDialog(t.title, onConfirm = { downloads?.delete(t.key) }, onDismiss = { askRemove = false; onDismiss() })
    else androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DuetColors.Surface,
        contentColor = DuetColors.Text,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
        ) {
            app.duet.music.ui.components.Artwork(t.artworkUrl, app.duet.music.ui.components.diagonal(0xFF2A2622, 0xFF141210), size = 52, radius = 8)
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Text(t.title, style = app.duet.music.ui.theme.DuetType.RowTitle, maxLines = 1)
                Text(t.artist, style = app.duet.music.ui.theme.DuetType.RowSub, color = DuetColors.TextDim, maxLines = 1)
            }
        }
        androidx.compose.material3.HorizontalDivider(color = DuetColors.Line, modifier = Modifier.padding(vertical = 6.dp))
        val item: @Composable (androidx.compose.ui.graphics.vector.ImageVector, String, () -> Unit) -> Unit = { icon, label, action ->
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth().clickable(role = androidx.compose.ui.semantics.Role.Button) { action() }.padding(horizontal = 24.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(18.dp),
            ) {
                androidx.compose.material3.Icon(icon, null, tint = DuetColors.Text, modifier = Modifier.size(22.dp))
                Text(label, style = app.duet.music.ui.theme.DuetType.RowTitle)
            }
        }
        if (!live) {
            val liked = graph.likes.isLiked(t)
            item(if (liked) DuetIcons.HeartFilled else DuetIcons.Heart, if (liked) "Remove from Liked songs" else "Like") { graph.toggleLike(t); onDismiss() }
        }
        item(DuetIcons.Next, "Play next") { graph.player.playNext(t); onDismiss() }
        if (!live) item(DuetIcons.Library, "Add to playlist") { picking = true }
        graph.sources[t.sourceId]?.shareUrl(t)?.let { url -> item(DuetIcons.Share, "Share") { graph.share("${t.title} by ${t.artist}\n$url"); onDismiss() } }
        if (!live) item(DuetIcons.Person, "Go to artist") { onOpenArtist(t); onDismiss() }
        item(DuetIcons.Queue, "Add to queue") { graph.player.addToQueue(t); onDismiss() }
        if (live) {
            val fav = graph.favouriteStations.isFavourite(t)
            item(if (fav) DuetIcons.HeartFilled else DuetIcons.Heart, if (fav) "Remove from favourites" else "Add to favourites") { graph.favouriteStations.toggle(t); onDismiss() }
        }
        if (!live) item(DuetIcons.Radio, "Start mix") { scope.launch { graph.player.playAll(graph.home.radioFor(t), 0) }; onDismiss() }
        if (downloads != null && !live) {
            if (saved) item(DuetIcons.Clear, "Remove download") { askRemove = true }
            else item(DuetIcons.Download, "Download") { downloads.download(t); onDismiss() }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp))
    }
}

/**
 * "Add to playlist": Duet playlists (songs from any source), a new one named here, and your playlists on the
 * song's own service (a Spotify song only into Spotify playlists, and so on).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AddToPlaylistSheet(graph: DuetGraph, t: Track, onDismiss: () -> Unit) {
    val playlists = graph.playlists
    val list by playlists.list.collectAsState()
    val source = graph.sources[t.sourceId]
    val signedIn = source?.account?.signedIn?.collectAsState()?.value == true
    val servicePlaylists = if (source != null && signedIn) source.addablePlaylists(graph.library.items.collectAsState().value) else emptyList()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Per service playlist: "Adding…", "Added", or why not.
    val status = remember { androidx.compose.runtime.mutableStateMapOf<String, String>() }
    var naming by remember { mutableStateOf(false) }
    if (naming) {
        NewPlaylistDialog(onCreate = { name -> playlists.add(playlists.create(name).id, t); onDismiss() }, onDismiss = onDismiss)
        return
    }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DuetColors.Surface,
        contentColor = DuetColors.Text,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Text(app.duet.music.ui.theme.DuetType.titled("Add to playlist"), style = app.duet.music.ui.theme.DuetType.SectionTitle, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        val row: @Composable (String, String, () -> Unit) -> Unit = { title, sub, action ->
            androidx.compose.foundation.layout.Column(
                Modifier.fillMaxWidth().clickable(role = androidx.compose.ui.semantics.Role.Button) { action() }.padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                Text(title, style = app.duet.music.ui.theme.DuetType.RowTitle)
                if (sub.isNotEmpty()) Text(sub, style = app.duet.music.ui.theme.DuetType.RowSub, color = DuetColors.TextDim)
            }
        }
        row("+ New playlist", "") { naming = true }
        list.forEach { p ->
            val has = p.tracks.any { it.key == t.key }
            row(p.name, if (has) "Already in it" else if (p.tracks.size == 1) "1 song" else "${p.tracks.size} songs") { if (!has) playlists.add(p.id, t); onDismiss() }
        }
        if (servicePlaylists.isNotEmpty()) {
            Text(
                "On ${source!!.manifest.name}".uppercase(), fontSize = 12.sp, color = DuetColors.TextDim, letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp),
            )
            servicePlaylists.forEach { p ->
                row(p.title, status[p.key] ?: p.subtitle) {
                    if (status[p.key] != null) return@row
                    status[p.key] = "Adding…"
                    scope.launch {
                        val ok = try { source.addToPlaylist(p, t) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { false }
                        status[p.key] = if (ok) "Added" else "Couldn't add: only playlists you made or can edit"
                        if (ok) { kotlinx.coroutines.delay(600); onDismiss() }
                    }
                }
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp))
    }
}

/** Asks for a new playlist's name. */
@Composable
fun NewPlaylistDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DuetColors.SurfaceRaised,
        titleContentColor = DuetColors.Text,
        title = { Text("New playlist") },
        text = {
            androidx.compose.material3.OutlinedTextField(
                name, { name = it }, singleLine = true, placeholder = { Text("Name", color = DuetColors.TextDim) },
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedTextColor = DuetColors.Text, unfocusedTextColor = DuetColors.Text,
                    focusedBorderColor = DuetColors.Accent, unfocusedBorderColor = DuetColors.Line, cursorColor = DuetColors.Accent,
                ),
            )
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onCreate(name) }) { Text("Create", color = DuetColors.Accent) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel", color = DuetColors.TextDim) } },
    )
}

/** Checks for a new version each time the app opens and offers it once per launch. */
@Composable
private fun UpdatePrompt(updates: app.duet.music.update.AppUpdates) {
    LaunchedEffect(Unit) { updates.check() }
    val state by updates.state.collectAsState()
    var dismissed by remember { mutableStateOf(false) }
    val info = when (val s = state) {
        is app.duet.music.update.UpdateState.Available -> s.info
        is app.duet.music.update.UpdateState.Downloading -> s.info
        else -> null
    }
    // On a computer an update installs by itself when Duet starts (once; if it fails or is cancelled the dialog is there to try again).
    val desktop = app.duet.music.ui.desktop.LocalDesktop.current
    var autoTried by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        val s = state
        if (desktop && !autoTried && s is app.duet.music.update.UpdateState.Available) { autoTried = true; updates.download(s.info) }
    }
    if (info == null || dismissed) return
    val downloading = state as? app.duet.music.update.UpdateState.Downloading
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { dismissed = true },
        containerColor = DuetColors.SurfaceRaised,
        titleContentColor = DuetColors.Text,
        textContentColor = DuetColors.TextDim,
        title = { Text("Duet ${info.versionName}") },
        text = {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                Text(info.notes.ifBlank { "A new version is ready." })
                if (downloading != null) {
                    if (downloading.progress < 0) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth(), color = DuetColors.Accent)
                    else androidx.compose.material3.LinearProgressIndicator({ downloading.progress }, Modifier.fillMaxWidth(), color = DuetColors.Accent)
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { updates.download(info) }, enabled = downloading == null) {
                Text("Update", color = DuetColors.Accent)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = { dismissed = true }) { Text("Not now", color = DuetColors.TextDim) }
        },
    )
}

/** Save / Saving / Saved for the playing song, when the device can save songs from its service. */
@Composable
private fun saveButton(graph: DuetGraph, track: Track?): SaveUi? {
    track ?: return null
    // Live radio can't be saved; the same button stars the station instead.
    if (Capability.LiveRadio in (graph.sources[track.sourceId]?.manifest?.capabilities ?: emptySet())) {
        val favourites by graph.favouriteStations.list.collectAsState()
        return SaveUi(if (favourites.any { it.key == track.key }) "Favourited" else "Favourite") { graph.favouriteStations.toggle(track) }
    }
    val downloads = graph.downloads ?: return null
    // Any song can be saved while some service can be downloaded from (Spotify songs are matched on YouTube Music).
    if (graph.sources.providers.none { Capability.Download in it.manifest.capabilities }) return null
    val saved by downloads.saved.collectAsState()
    val progress by downloads.inProgress.collectAsState()
    val p = progress[track.key]
    var askRemove by remember(track.key) { mutableStateOf(false) }
    if (askRemove) app.duet.music.ui.components.RemoveDownloadDialog(track.title, onConfirm = { downloads.delete(track.key) }, onDismiss = { askRemove = false })
    return when {
        saved.any { it.key == track.key } -> SaveUi("Saved") { askRemove = true }
        p != null -> SaveUi(if (p < 0) "Saving…" else "Saving ${(p * 100).toInt()}%") { }
        else -> SaveUi("Save") { downloads.download(track) }
    }
}

private fun PlayerState.toMiniPlayerUi(sources: SourceRegistry): MiniPlayerUi? {
    val t = track ?: return null
    val source = sources[t.sourceId]
    val duration = durationMs?.takeIf { it > 0 }
    return MiniPlayerUi(
        title = t.title,
        sourceName = listOfNotNull(source?.manifest?.name, t.artist.takeIf { it.isNotBlank() }).joinToString(" · "),
        sourceColor = Color(source?.color ?: 0xFF9AA0B4),
        artworkUrl = t.artworkUrl,
        isPlaying = status == PlaybackStatus.Playing,
        isLoading = status == PlaybackStatus.Loading,
        progress = duration?.let { (positionMs.toFloat() / it).coerceIn(0f, 1f) },
        error = error?.takeIf { status == PlaybackStatus.Error }?.let { "Can't play: $it" },
    )
}

/** Songs played before that the cache holds completely (they play offline), newest first. */
@Composable
private fun cachedSongs(graph: DuetGraph): List<app.duet.music.domain.Track> {
    val keys = graph.cache?.cachedKeys?.collectAsState()?.value ?: return emptyList()
    return graph.history.recent.collectAsState().value.filter { it.key in keys }
}

/** The local hour of the day (0-23), for Home's greeting. */
expect fun currentHour(): Int

/** Now, in seconds since 1970 (scrobble times). */
expect fun epochSeconds(): Long
