package com.ayam.video

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Library.init(applicationContext)
        Pal.load(applicationContext)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Pal.bg
                ) {
                    val viewModel: VideoViewModel = viewModel()
                    MainAppRoot(viewModel)
                }
            }
        }
    }
}

sealed interface UiState {
    object Loading : UiState
    data class Success(val videos: List<VideoItem>) : UiState
    data class Error(val message: String) : UiState
}

class VideoViewModel : ViewModel() {
    var uiState by mutableStateOf<UiState>(UiState.Loading)
        private set

    private val _favoritesList = mutableStateListOf<VideoItem>().apply { addAll(Library.load("favorites")) }
    val favoritesList: List<VideoItem> get() = _favoritesList

    private val _downloadedList = mutableStateListOf<VideoItem>().apply { addAll(Library.load("downloads")) }
    val downloadedList: List<VideoItem> get() = _downloadedList

    private var fetchJob: Job? = null
    private var trendsSource: String? = "__aucune__"

    /** Recharge les tendances seulement si la source a changé ou si la dernière tentative a échoué. */
    fun ensureTrends(context: Context) {
        if (trendsSource != SearchHolder.source || uiState is UiState.Error) {
            fetchDynamicVideos(context)
        }
    }

    fun fetchDynamicVideos(context: Context) {
        fetchJob?.cancel()
        val source = SearchHolder.source
        trendsSource = source
        fetchJob = viewModelScope.launch {
            uiState = UiState.Loading
            try {
                val remoteVideos = VideoEngine.trending(context, source, 30)
                if (remoteVideos.isEmpty()) {
                    uiState = UiState.Error("Aucune tendance disponible pour le moment.")
                } else {
                    uiState = UiState.Success(remoteVideos)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = UiState.Error("Impossible de charger les flux vidéo. Vérifiez votre connexion.")
            }
        }
    }

    fun toggleFavorite(video: VideoItem) {
        val existing = _favoritesList.firstOrNull { it.id == video.id }
        if (existing != null) {
            _favoritesList.remove(existing)
        } else {
            _favoritesList.add(video)
        }
        Library.save("favorites", _favoritesList)
    }

    fun isFavorite(video: VideoItem): Boolean = _favoritesList.any { it.id == video.id }

    fun downloadVideo(video: VideoItem, localUri: String = "") {
        if (_downloadedList.none { it.id == video.id }) {
            _downloadedList.add(video.copy(localUri = localUri))
            Library.save("downloads", _downloadedList)
        }
    }

    fun isDownloaded(video: VideoItem): Boolean = _downloadedList.any { it.id == video.id }

    fun removeDownload(context: Context, video: VideoItem, deleteFile: Boolean) {
        val existing = _downloadedList.firstOrNull { it.id == video.id } ?: return
        if (deleteFile) DownloadHolder.deleteFile(context, existing.localUri)
        _downloadedList.remove(existing)
        Library.save("downloads", _downloadedList)
    }
}

data class VideoItem(
    val id: String,
    val title: String, 
    val details: String, 
    val source: String,
    val videoUrl: String,
    val thumbnailUrl: String = "",
    val localUri: String = ""
)

@Composable
fun MainAppRoot(viewModel: VideoViewModel) {
    // Pile de vidéos ouvertes : « Retour » revient à la vidéo précédente
    val stack = remember { mutableStateListOf<VideoItem>() }
    val current = stack.lastOrNull()

    if (current != null) {
        BackHandler { stack.removeAt(stack.lastIndex) }
        key(current.id) {
            PlayerScreen(
                video = current,
                viewModel = viewModel,
                onBack = { stack.removeAt(stack.lastIndex) },
                onOpenVideo = { stack.add(it) }
            )
        }
    } else {
        MainScreen(
            viewModel = viewModel,
            onVideoClick = { video -> stack.add(video) }
        )
    }
}

@Composable
fun MainScreen(
    viewModel: VideoViewModel,
    onVideoClick: (VideoItem) -> Unit
) {
    var selectedTab by SearchHolder.tabState
    val items = listOf("Accueil", "Bibliothèque", "Paramètres")
    val icons = listOf(Icons.Default.Home, Icons.Default.PlayArrow, Icons.Default.Settings)

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(
                containerColor = Pal.surface,
                contentColor = Pal.accent
            ) {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = { Icon(icons[index], contentDescription = item) },
                        label = { Text(item, fontSize = 10.sp) },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFFFFFFFF),
                            selectedTextColor = Pal.accent,
                            unselectedIconColor = Pal.muted2,
                            unselectedTextColor = Pal.muted2,
                            indicatorColor = Pal.surface2
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Pal.bgTop,
                            Pal.bg
                        )
                    )
                )
        ) {
            when (selectedTab) {
                0 -> HomeScreen(viewModel = viewModel, onVideoClick = onVideoClick)
                1 -> LibraryScreen(viewModel = viewModel, onVideoClick = onVideoClick)
                2 -> SettingsScreen()
            }
        }
    }
}

@Composable
fun HomeScreen(viewModel: VideoViewModel, onVideoClick: (VideoItem) -> Unit) {
    val scrollState = rememberScrollState()
    val state = viewModel.uiState
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    LaunchedEffect(Unit) {
        viewModel.ensureTrends(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "AYAM VIDEO",
            color = Pal.accent,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        SearchBar(
            onSearch = {
                focus.clearFocus()
                scope.launch { runSearch(context) }
            },
            onClear = {
                clearSearch()
                viewModel.ensureTrends(context)
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        val options = listOf<String?>(null) + SOURCE_NAMES
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { opt ->
                SourceChip(
                    label = opt ?: "Tout",
                    selected = SearchHolder.source == opt,
                    onClick = {
                        if (SearchHolder.source != opt) {
                            SearchHolder.source = opt
                            if (SearchHolder.searched) {
                                scope.launch { runSearch(context) }
                            } else {
                                viewModel.fetchDynamicVideos(context)
                            }
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (SearchHolder.searched) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (SearchHolder.loading) "Recherche…" else "Résultats (${SearchHolder.results.size})",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = {
                    clearSearch()
                    viewModel.ensureTrends(context)
                }) {
                    Text("Effacer", color = Pal.accent)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            val err = SearchHolder.error
            when {
                SearchHolder.loading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Pal.accent)
                    }
                }
                err != null -> {
                    Text(text = err, color = Color(0xFFFF8080), fontSize = 14.sp)
                }
                SearchHolder.results.isEmpty() -> {
                    Text(text = "Aucun résultat trouvé", color = Pal.muted, fontSize = 14.sp)
                }
                else -> {
                    SearchHolder.results.forEach { video ->
                        VideoRow(video = video, onClick = { onVideoClick(video) })
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tendances actuelles",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = { viewModel.fetchDynamicVideos(context) }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Rafraîchir", tint = Pal.accent)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (state) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Pal.accent)
                    }
                }
                is UiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 30.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = state.message,
                            color = Color(0xFFFF8080),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.fetchDynamicVideos(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = Pal.surface2)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Pal.accent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Réessayer", color = Pal.accent)
                        }
                    }
                }
                is UiState.Success -> {
                    state.videos.forEachIndexed { index, video ->
                        if (index == 0) {
                            VideoCard(video = video, onClick = { onVideoClick(video) })
                        } else {
                            VideoRow(video = video, onClick = { onVideoClick(video) })
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun SourceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) Pal.surface2 else Pal.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (selected) Pal.accent else Pal.border
        )
    ) {
        Text(
            text = label,
            color = if (selected) Pal.accent else Pal.muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun VideoRow(video: VideoItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Pal.surface)
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(112.dp)
                .height(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Pal.surface2),
            contentAlignment = Alignment.Center
        ) {
            if (video.thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(video.thumbnailUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Pal.accent)
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = video.details,
                color = Pal.muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = video.source,
                color = Pal.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun LibraryScreen(viewModel: VideoViewModel, onVideoClick: (VideoItem) -> Unit) {
    var isDownloadsTab by remember { mutableStateOf(false) }
    var toRemove by remember { mutableStateOf<VideoItem?>(null) }
    val context = LocalContext.current

    toRemove?.let { v ->
        AlertDialog(
            onDismissRequest = { toRemove = null },
            containerColor = Pal.surface,
            title = { Text("Supprimer ce téléchargement ?", color = Pal.accent) },
            text = { Text(v.title, color = Pal.muted, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeDownload(context, v, true)
                    toRemove = null
                }) { Text("Supprimer le fichier", color = Color(0xFFFF8080)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        viewModel.removeDownload(context, v, false)
                        toRemove = null
                    }) { Text("Retirer de la liste", color = Pal.muted) }
                    TextButton(onClick = { toRemove = null }) { Text("Annuler", color = Pal.muted) }
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Bibliothèque",
            color = Pal.accent,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { isDownloadsTab = false },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!isDownloadsTab) Pal.surface2 else Pal.surface,
                    contentColor = if (!isDownloadsTab) Pal.accent else Pal.muted2
                ),
                shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp, topEnd = 0.dp, bottomEnd = 0.dp),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Text("Mes Favoris")
            }
            Button(
                onClick = { isDownloadsTab = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDownloadsTab) Pal.surface2 else Pal.surface,
                    contentColor = if (isDownloadsTab) Pal.accent else Pal.muted2
                ),
                shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 12.dp, bottomEnd = 12.dp),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Text("Téléchargements")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        val currentList = if (isDownloadsTab) viewModel.downloadedList else viewModel.favoritesList

        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            if (currentList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (isDownloadsTab) Icons.Default.Check else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            tint = Pal.muted2,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (isDownloadsTab) "Aucune vidéo téléchargée" else "Aucune vidéo dans vos favoris",
                            color = Pal.muted,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                currentList.forEach { video ->
                    VideoCard(
                        video = video,
                        onClick = { onVideoClick(video) },
                        onRemove = {
                            if (isDownloadsTab) {
                                toRemove = video
                            } else {
                                viewModel.toggleFavorite(video)
                                Toast.makeText(context, "Retiré des favoris", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

private fun dirSize(f: java.io.File): Long =
    if (f.isDirectory) (f.listFiles()?.sumOf { dirSize(it) } ?: 0L) else f.length()

fun cacheSizeBytes(context: Context): Long =
    dirSize(context.cacheDir) + (context.externalCacheDir?.let { dirSize(it) } ?: 0L)

fun formatBytes(b: Long): String = when {
    b >= 1_048_576L -> String.format(java.util.Locale.US, "%.1f Mo", b / 1_048_576.0).replace('.', ',')
    b >= 1024L -> "${b / 1024} Ko"
    else -> "$b o"
}

fun clearCache(context: Context) {
    try {
        val loader = coil.Coil.imageLoader(context)
        loader.memoryCache?.clear()
        loader.diskCache?.clear()
    } catch (e: Exception) { }
    val downloading = DownloadHolder.progress.isNotEmpty()
    val roots = listOfNotNull(context.cacheDir, context.externalCacheDir)
    roots.forEach { root ->
        root.listFiles()?.forEach { f ->
            if (downloading && f.name.startsWith("ayam_")) return@forEach
            f.deleteRecursively()
        }
    }
}

fun autoPlayEnabled(context: Context): Boolean =
    context.getSharedPreferences("ayam_settings", Context.MODE_PRIVATE).getBoolean("autoplay", true)

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ayam_settings", Context.MODE_PRIVATE) }

    var autoPlay by remember { mutableStateOf(prefs.getBoolean("autoplay", true)) }
    var themesOpen by remember { mutableStateOf(false) }
    var cacheSize by remember { mutableStateOf(cacheSizeBytes(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Paramètres",
            color = Pal.accent,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        SettingsSectionTitle("Apparence")
        Spacer(modifier = Modifier.height(8.dp))
        SettingsActionItem(
            title = "Thème de l'application",
            subtitle = Pal.theme.name + (if (themesOpen) "  ▴" else "  ▾"),
            onClick = { themesOpen = !themesOpen }
        )
        if (themesOpen) {
            AppThemes.all.forEach { t ->
                val selected = t.name == Pal.theme.name
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = t.surface),
                    border = androidx.compose.foundation.BorderStroke(
                        if (selected) 2.dp else 1.dp,
                        if (selected) t.accent else t.border
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { Pal.select(context, t) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(t.bgTop, t.surface2, t.accent).forEach { c ->
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .background(c, shape = RoundedCornerShape(50))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = t.name,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        if (selected) Text("✓", color = t.accent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        SettingsSectionTitle("Données et Cache")
        Spacer(modifier = Modifier.height(8.dp))
        SettingsActionItem(
            title = "Effacer le cache",
            subtitle = formatBytes(cacheSize) + " utilisés (images et fichiers temporaires)",
            onClick = {
                clearCache(context)
                cacheSize = cacheSizeBytes(context)
                Toast.makeText(context, "Cache effacé", Toast.LENGTH_SHORT).show()
            }
        )
        SettingsToggleItem(
            title = "Lecture automatique",
            checked = autoPlay,
            onCheckedChange = {
                autoPlay = it
                prefs.edit().putBoolean("autoplay", it).apply()
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        SettingsSectionTitle("À propos")
        Spacer(modifier = Modifier.height(8.dp))
        SettingsStaticItem(title = "Version de l'application", subtitle = "AYAM VIDEO v1.4.0")
        SettingsStaticItem(title = "Développé par", subtitle = "AYAM Abdoul Lab")

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        color = Pal.accent,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun SettingsStaticItem(title: String, subtitle: String) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Pal.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, color = Pal.muted, fontSize = 12.sp)
        }
    }
}

@Composable
fun SettingsToggleItem(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Pal.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Switch(
                checked = checked, 
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Pal.accent)
            )
        }
    }
}

@Composable
fun SettingsActionItem(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Pal.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, color = Pal.muted, fontSize = 12.sp)
        }
    }
}

@Composable
fun VideoCard(video: VideoItem, onClick: () -> Unit, onRemove: (() -> Unit)? = null) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Pal.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(Pal.surface2),
                contentAlignment = Alignment.Center
            ) {
                if (video.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(video.thumbnailUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = video.title,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = Pal.surface.copy(alpha = 0.67f),
                    modifier = Modifier.size(50.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Lire",
                            tint = Pal.accent
                        )
                    }
                }
                
                if (onRemove != null) {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Pal.bg.copy(alpha = 0.67f), shape = RoundedCornerShape(50)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Supprimer",
                                tint = Color(0xFFFF8080),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Pal.bg.copy(alpha = 0.67f), shape = RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                    Text(
                        text = video.source,
                        color = Pal.accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = video.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = video.details,
                    color = Pal.muted,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
private fun startPlay(player: ExoPlayer, opt: PlayOption, startMs: Long, play: Boolean = true) {
    if (opt.audioUrl.isBlank()) {
        player.setMediaItem(MediaItem.fromUri(Uri.parse(opt.url)))
    } else {
        val factory = ProgressiveMediaSource.Factory(DefaultHttpDataSource.Factory())
        val video = factory.createMediaSource(MediaItem.fromUri(Uri.parse(opt.url)))
        val audio = factory.createMediaSource(MediaItem.fromUri(Uri.parse(opt.audioUrl)))
        player.setMediaSource(MergingMediaSource(video, audio))
    }
    player.prepare()
    if (startMs > 0) player.seekTo(startMs)
    player.playWhenReady = play
}

@Composable
fun PlayerScreen(
    video: VideoItem,
    viewModel: VideoViewModel,
    onBack: () -> Unit,
    onOpenVideo: (VideoItem) -> Unit
) {
    val context = LocalContext.current
    val isFavorite = viewModel.isFavorite(video)
    val isDownloaded = viewModel.isDownloaded(video)

    var isFullscreen by remember { mutableStateOf(false) }
    var streamLoading by remember { mutableStateOf(true) }
    var streamError by remember { mutableStateOf<String?>(null) }
    var showQuality by remember { mutableStateOf(false) }
    var showPlayQuality by remember { mutableStateOf(false) }
    var playOptions by remember { mutableStateOf<List<PlayOption>>(emptyList()) }
    var selectedPlay by remember { mutableStateOf<PlayOption?>(null) }
    var related by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var retryKey by remember { mutableStateOf(0) }

    if (showQuality) {
        QualityDialog(
            video = video,
            onDismiss = { showQuality = false },
            onPick = { option ->
                showQuality = false
                DownloadHolder.start(context, video, option) { uri ->
                    viewModel.downloadVideo(video, uri)
                }
            }
        )
    }

    val exoPlayer = remember(context) {
        try {
            ExoPlayer.Builder(context).build()
        } catch (e: Exception) {
            null
        }
    }

    DisposableEffect(exoPlayer) {
        if (exoPlayer != null) {
            val listener = object : androidx.media3.common.Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    streamError = "Erreur de lecture : " + (error.cause?.message ?: error.errorCodeName).take(150)
                }
            }
            exoPlayer.addListener(listener)
            onDispose {
                try {
                    exoPlayer.removeListener(listener)
                    exoPlayer.release()
                } catch (ignored: Exception) {}
            }
        } else {
            onDispose {}
        }
    }

    LaunchedEffect(video.videoUrl, exoPlayer, retryKey) {
        if (exoPlayer == null) {
            streamError = "Impossible d'initialiser le lecteur vidéo."
            streamLoading = false
            return@LaunchedEffect
        }
        streamLoading = true
        streamError = null
        try {
            if (video.localUri.isNotBlank()) {
                exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(video.localUri)))
                exoPlayer.prepare()
                exoPlayer.playWhenReady = autoPlayEnabled(context)
            } else if (video.videoUrl.endsWith(".mp4")) {
                exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(video.videoUrl)))
                exoPlayer.prepare()
                exoPlayer.playWhenReady = autoPlayEnabled(context)
            } else {
                val details = VideoEngine.loadDetails(context, video.videoUrl, video.source)
                playOptions = details.playOptions
                related = details.related
                val first = details.playOptions.firstOrNull { it.isDefault }
                    ?: details.playOptions.first()
                selectedPlay = first
                startPlay(exoPlayer, first, 0L, autoPlayEnabled(context))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            streamError = "Erreur de lecture : " + (e.message ?: e.javaClass.simpleName).take(150)
        }
        streamLoading = false
    }

    // Vidéo déjà téléchargée : on charge quand même les suggestions
    LaunchedEffect(video.id) {
        if (video.localUri.isNotBlank() && video.videoUrl.startsWith("http") && !video.videoUrl.endsWith(".mp4")) {
            try {
                related = VideoEngine.loadDetails(context, video.videoUrl, video.source).related
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // pas de suggestions, ce n'est pas grave
            }
        }
    }

    if (showPlayQuality) {
        AlertDialog(
            onDismissRequest = { showPlayQuality = false },
            containerColor = Pal.surface,
            title = { Text("Qualité de lecture", color = Pal.accent) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    playOptions.forEach { opt ->
                        val isSel = opt == selectedPlay
                        OutlinedButton(
                            onClick = {
                                showPlayQuality = false
                                if (!isSel && exoPlayer != null) {
                                    val pos = exoPlayer.currentPosition
                                    val wasPlaying = exoPlayer.playWhenReady
                                    selectedPlay = opt
                                    try {
                                        startPlay(exoPlayer, opt, pos, wasPlaying)
                                    } catch (e: Throwable) {
                                        streamError = "Erreur de lecture : " + (e.message ?: e.javaClass.simpleName).take(150)
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = opt.label + if (isSel) "  ✓" else "",
                                color = if (isSel) Pal.accent else Color.White,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text(
                        text = "Les qualités HD consomment plus de données.",
                        color = Pal.muted,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPlayQuality = false }) {
                    Text("Annuler", color = Pal.muted)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Pal.bg)
            .then(if (isFullscreen) Modifier else Modifier.verticalScroll(rememberScrollState()))
            .padding(if (isFullscreen) 0.dp else 16.dp)
    ) {
        if (!isFullscreen) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = Pal.accent)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Lecture en cours", color = Pal.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (exoPlayer != null) {
                        IconButton(onClick = { isFullscreen = !isFullscreen }) {
                            Text("[ 🗖 ]", color = Pal.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    IconButton(onClick = { viewModel.toggleFavorite(video) }) {
                        Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Favori", tint = Pal.accent)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isFullscreen) Modifier.weight(1f) else Modifier.height(220.dp))
                .background(Color.Black, shape = if (isFullscreen) RoundedCornerShape(0.dp) else RoundedCornerShape(12.dp))
        ) {
            if (selectedPlay?.isAudio == true && video.thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(video.thumbnailUrl).build(),
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (exoPlayer != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                            setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Lecteur indisponible", color = Color.White)
                }
            }

            if (isFullscreen) {
                IconButton(
                    onClick = { isFullscreen = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).background(Pal.surface.copy(alpha = 0.67f), shape = RoundedCornerShape(50))
                ) {
                    Text("✕", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            if (streamLoading) {
                CircularProgressIndicator(color = Pal.accent, modifier = Modifier.align(Alignment.Center))
            }
            streamError?.let { err ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Pal.bg.copy(alpha = 0.93f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                        Text(text = err, color = Color(0xFFFF8080), fontSize = 13.sp, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { retryKey++ },
                            colors = ButtonDefaults.buttonColors(containerColor = Pal.surface2)
                        ) {
                            Text("Réessayer", color = Pal.accent)
                        }
                    }
                }
            }
        }

        if (!isFullscreen) {
            if (playOptions.size > 1 && selectedPlay != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    onClick = { showPlayQuality = true },
                    shape = RoundedCornerShape(20.dp),
                    color = Pal.surface2,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Pal.accent)
                ) {
                    Text(
                        text = "Qualité : " + selectedPlay!!.label + "  ▾",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = video.title, color = Pal.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = video.details,
                        color = Pal.muted,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Surface(
                    onClick = {
                        if (!isDownloaded && DownloadHolder.canStart(video.id)) {
                            showQuality = true
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDownloaded) Pal.surface2 else Pal.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Pal.border),
                    modifier = Modifier.padding(start = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (isDownloaded) Icons.Default.Check else Icons.Default.Add,
                            contentDescription = "Télécharger",
                            tint = if (isDownloaded) Pal.accent else Pal.muted2
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = downloadLabel(isDownloaded, DownloadHolder.progress[video.id]),
                            color = if (isDownloaded) Color.White else Pal.muted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (related.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Suggestions",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                related.forEach { v ->
                    VideoRow(video = v, onClick = { onOpenVideo(v) })
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
