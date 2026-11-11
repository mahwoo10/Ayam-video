package com.ayam.video

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import androidx.compose.ui.graphics.Color
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Library.init(applicationContext)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF090412)
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

    fun fetchDynamicVideos(context: Context, query: String = "tendance musique") {
        viewModelScope.launch {
            uiState = UiState.Loading
            try {
                // AUGMENTATION DE LA LIMITE À 30 RÉSULTATS
                val remoteVideos = VideoEngine.search(context, query, 30)
                if (remoteVideos.isEmpty()) {
                    uiState = UiState.Error("Aucun résultat trouvé.")
                } else {
                    uiState = UiState.Success(remoteVideos)
                }
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
    var currentVideo by remember { mutableStateOf<VideoItem?>(null) }
    val context = LocalContext.current
    val activity = context as? Activity

    if (currentVideo != null) {
        BackHandler {
            currentVideo = null
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        PlayerScreen(
            video = currentVideo!!, 
            viewModel = viewModel,
            onBack = { 
                currentVideo = null 
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        )
    } else {
        MainScreen(
            viewModel = viewModel,
            onVideoClick = { video -> currentVideo = video }
        )
    }
}

@Composable
fun MainScreen(
    viewModel: VideoViewModel,
    onVideoClick: (VideoItem) -> Unit
) {
    var selectedTab by SearchHolder.tabState
    val items = listOf("Accueil", "Recherche", "Bibliothèque", "Paramètres")
    val icons = listOf(Icons.Default.Home, Icons.Default.Search, Icons.Default.PlayArrow, Icons.Default.Settings)

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF130826),
                contentColor = Color(0xFFE0AAFF)
            ) {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = { Icon(icons[index], contentDescription = item) },
                        label = { Text(item, fontSize = 10.sp) },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFFFFFFFF),
                            selectedTextColor = Color(0xFFE0AAFF),
                            unselectedIconColor = Color(0xFF7A6B9C),
                            unselectedTextColor = Color(0xFF7A6B9C),
                            indicatorColor = Color(0xFF2E1A47)
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
                            Color(0xFF1B0B3A),
                            Color(0xFF090412)
                        )
                    )
                )
        ) {
            when (selectedTab) {
                0 -> HomeScreen(viewModel = viewModel, onVideoClick = onVideoClick)
                1 -> RealSearchScreen(onVideoClick = onVideoClick)
                2 -> LibraryScreen(viewModel = viewModel, onVideoClick = onVideoClick)
                3 -> SettingsScreen()
            }
        }
    }
}

@Composable
fun HomeScreen(viewModel: VideoViewModel, onVideoClick: (VideoItem) -> Unit) {
    val scrollState = rememberScrollState()
    val state = viewModel.uiState
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        if (state is UiState.Loading) {
            viewModel.fetchDynamicVideos(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "AYAM VIDEO",
            color = Color(0xFFE0AAFF),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        val sources = listOf("YouTube", "Vimeo", "Dailymotion", "TikTok")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            sources.forEach { source ->
                SourceBadge(name = source)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

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
                Icon(Icons.Default.Refresh, contentDescription = "Rafraîchir", tint = Color(0xFFE0AAFF))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (state) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFE0AAFF))
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
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E1A47))
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFFE0AAFF))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Réessayer", color = Color(0xFFE0AAFF))
                    }
                }
            }
            is UiState.Success -> {
                state.videos.forEach { video ->
                    VideoCard(video = video, onClick = { onVideoClick(video) })
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun SourceBadge(name: String) {
    var isSelected by remember { mutableStateOf(true) }

    Surface(
        onClick = { isSelected = !isSelected },
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) Color(0xFF2E1A47) else Color(0xFF130826),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isSelected) Color(0xFFE0AAFF) else Color(0xFF3B2859)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color(0xFFE0AAFF),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = name,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun LibraryScreen(viewModel: VideoViewModel, onVideoClick: (VideoItem) -> Unit) {
    var isDownloadsTab by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Bibliothèque",
            color = Color(0xFFE0AAFF),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { isDownloadsTab = false },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!isDownloadsTab) Color(0xFF2E1A47) else Color(0xFF130826),
                    contentColor = if (!isDownloadsTab) Color(0xFFE0AAFF) else Color(0xFF7A6B9C)
                ),
                shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp, topEnd = 0.dp, bottomEnd = 0.dp),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Text("Mes Favoris")
            }
            Button(
                onClick = { isDownloadsTab = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDownloadsTab) Color(0xFF2E1A47) else Color(0xFF130826),
                    contentColor = if (isDownloadsTab) Color(0xFFE0AAFF) else Color(0xFF7A6B9C)
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
                            tint = Color(0xFF7A6B9C),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (isDownloadsTab) "Aucune vidéo téléchargée" else "Aucune vidéo dans vos favoris",
                            color = Color(0xFF9A8CB9),
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                currentList.forEach { video ->
                    VideoCard(video = video, onClick = { onVideoClick(video) })
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var isDarkTheme by remember { mutableStateOf(true) }
    var autoPlay by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Paramètres",
            color = Color(0xFFE0AAFF),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        SettingsSectionTitle("Apparence")
        Spacer(modifier = Modifier.height(8.dp))
        SettingsStaticItem(title = "Thème de l'application", subtitle = "Saphir & Améthyste (Actif)")
        SettingsToggleItem(title = "Mode sombre automatique", checked = isDarkTheme, onCheckedChange = { isDarkTheme = it })

        Spacer(modifier = Modifier.height(24.dp))

        SettingsSectionTitle("Données et Cache")
        Spacer(modifier = Modifier.height(8.dp))
        SettingsActionItem(title = "Effacer le cache vidéo", subtitle = "12.4 Mo utilisés", onClick = {
            Toast.makeText(context, "Cache nettoyé avec succès", Toast.LENGTH_SHORT).show()
        })
        SettingsToggleItem(title = "Lecture automatique", checked = autoPlay, onCheckedChange = { autoPlay = it })

        Spacer(modifier = Modifier.height(24.dp))

        SettingsSectionTitle("À propos")
        Spacer(modifier = Modifier.height(8.dp))
        SettingsStaticItem(title = "Version de l'application", subtitle = "AYAM VIDEO v1.3.0")
        SettingsStaticItem(title = "Développé par", subtitle = "AYAM Abdoul Lab")
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        color = Color(0xFFE0AAFF),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun SettingsStaticItem(title: String, subtitle: String) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF130826)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, color = Color(0xFF9A8CB9), fontSize = 12.sp)
        }
    }
}

@Composable
fun SettingsToggleItem(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF130826)),
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
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFFE0AAFF))
            )
        }
    }
}

@Composable
fun SettingsActionItem(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF130826)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, color = Color(0xFF9A8CB9), fontSize = 12.sp)
        }
    }
}

@Composable
fun VideoCard(video: VideoItem, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF130826)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(Color(0xFF1F103F)),
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
                    color = Color(0xAA130826),
                    modifier = Modifier.size(50.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Lire",
                            tint = Color(0xFFE0AAFF)
                        )
                    }
                }
                
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color(0xAA090412), shape = RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                    Text(
                        text = video.source,
                        color = Color(0xFFE0AAFF),
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
                    color = Color(0xFF9A8CB9),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun PlayerScreen(
    video: VideoItem, 
    viewModel: VideoViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val isFavorite = viewModel.isFavorite(video)
    val isDownloaded = viewModel.isDownloaded(video)
    
    var isFullscreen by remember { mutableStateOf(activity?.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) }
    var streamLoading by remember { mutableStateOf(true) }
    var streamError by remember { mutableStateOf<String?>(null) }
    var showQuality by remember { mutableStateOf(false) }
    
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
    val exoPlayer = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(exoPlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                streamError = "Lecteur : " + error.errorCodeName + " " + (error.cause?.message ?: "").take(200)
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(video.videoUrl) {
        streamLoading = true
        streamError = null
        try {
            val playUrl = if (video.localUri.isNotBlank()) {
                video.localUri
            } else if (video.videoUrl.endsWith(".mp4")) {
                video.videoUrl
            } else {
                VideoEngine.resolveStream(context, video.videoUrl)
            }
            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(playUrl)))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        } catch (e: Exception) {
            streamError = "Extraction : " + (e.message ?: e.toString()).take(300)
        }
        streamLoading = false
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090412))
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
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = Color(0xFFE0AAFF))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Lecture en cours", color = Color(0xFFE0AAFF), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { 
                        isFullscreen = true
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    }) {
                        Text("[ ]", color = Color(0xFFE0AAFF), fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { viewModel.toggleFavorite(video) }) {
                        Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Favori", tint = Color(0xFFE0AAFF))
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(if (isFullscreen) 1f else 0f)
                .height(if (isFullscreen) Dp.Unspecified else 220.dp)
                .background(Color.Black, shape = if(isFullscreen) RoundedCornerShape(0.dp) else RoundedCornerShape(12.dp))
        ) {
            AndroidView(
                factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer } },
                modifier = Modifier.fillMaxSize()
            )
            
            if (isFullscreen) {
                IconButton(
                    onClick = { 
                        isFullscreen = false
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).background(Color(0x88000000), shape = RoundedCornerShape(50))
                ) {
                    Text("X", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            if (streamLoading) {
                CircularProgressIndicator(color = Color(0xFFE0AAFF), modifier = Modifier.align(Alignment.Center))
            }
            streamError?.let {
                Text(text = it, color = Color(0xFFFF8080), fontSize = 11.sp, modifier = Modifier.align(Alignment.Center).padding(8.dp))
            }
        }

        if (!isFullscreen) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = video.title, color = Color(0xFFE0AAFF), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = video.details, 
                        color = Color(0xFF9A8CB9), 
                        fontSize = 14.sp,
                        lineHeight = 20.sp, // CORRECTION DE L'AFFICHAGE DU TEXTE
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
                    color = if (isDownloaded) Color(0xFF2E1A47) else Color(0xFF130826),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B2859)),
                    modifier = Modifier.padding(start = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (isDownloaded) Icons.Default.Check else Icons.Default.Add,
                            contentDescription = "Télécharger",
                            tint = if (isDownloaded) Color(0xFFE0AAFF) else Color(0xFF7A6B9C)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = downloadLabel(isDownloaded, DownloadHolder.progress[video.id]),
                            color = if (isDownloaded) Color.White else Color(0xFF9A8CB9),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
