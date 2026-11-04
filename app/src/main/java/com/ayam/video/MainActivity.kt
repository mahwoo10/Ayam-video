package com.ayam.video

import android.net.Uri
import android.os.Bundle
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    private val _favoritesList = mutableStateListOf<VideoItem>()
    val favoritesList: List<VideoItem> get() = _favoritesList

    private val _downloadedList = mutableStateListOf<VideoItem>()
    val downloadedList: List<VideoItem> get() = _downloadedList

    init {
        fetchDynamicVideos()
    }

    fun fetchDynamicVideos() {
        viewModelScope.launch {
            uiState = UiState.Loading
            try {
                delay(1500)
                
                val remoteVideos = listOf(
                    VideoItem(
                        id = "vid_1",
                        title = "Big Buck Bunny (Animation Test)", 
                        details = "Blender Foundation • 60 FPS", 
                        source = "YouTube",
                        videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                        thumbnailUrl = "https://images.unsplash.com/photo-1568605117036-5fe5e7bab0b7?w=600&auto=format&fit=crop&q=80"
                    ),
                    VideoItem(
                        id = "vid_2",
                        title = "Elephant's Dream (Open Movie)", 
                        details = "Blender Project • HD", 
                        source = "Vimeo",
                        videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                        thumbnailUrl = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=600&auto=format&fit=crop&q=80"
                    ),
                    VideoItem(
                        id = "vid_3",
                        title = "For Bigger Blazes (Trailer)", 
                        details = "Google Samples • 4K", 
                        source = "Dailymotion",
                        videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                        thumbnailUrl = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=600&auto=format&fit=crop&q=80"
                    ),
                    VideoItem(
                        id = "vid_4",
                        title = "Jetpack Compose Tutoriel Avancé", 
                        details = "Android Dev • 15K vues", 
                        source = "YouTube",
                        videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                        thumbnailUrl = "https://images.unsplash.com/photo-1550745165-9bc0b252726f?w=600&auto=format&fit=crop&q=80"
                    )
                )
                uiState = UiState.Success(remoteVideos)
            } catch (e: Exception) {
                uiState = UiState.Error("Impossible de charger les flux vidéo. Vérifiez votre connexion.")
            }
        }
    }

    fun toggleFavorite(video: VideoItem) {
        if (_favoritesList.contains(video)) {
            _favoritesList.remove(video)
        } else {
            _favoritesList.add(video)
        }
    }

    fun isFavorite(video: VideoItem): Boolean = _favoritesList.contains(video)

    fun downloadVideo(video: VideoItem) {
        if (!_downloadedList.contains(video)) {
            _downloadedList.add(video)
        }
    }

    fun isDownloaded(video: VideoItem): Boolean = _downloadedList.contains(video)
}

data class VideoItem(
    val id: String,
    val title: String, 
    val details: String, 
    val source: String,
    val videoUrl: String,
    val thumbnailUrl: String = ""
)

@Composable
fun MainAppRoot(viewModel: VideoViewModel) {
    var currentVideo by remember { mutableStateOf<VideoItem?>(null) }

    if (currentVideo != null) {
        BackHandler {
            currentVideo = null
        }

        PlayerScreen(
            video = currentVideo!!, 
            viewModel = viewModel,
            onBack = { currentVideo = null }
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
    var selectedTab by remember { mutableStateOf(0) }
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
                1 -> SearchScreen(viewModel = viewModel, onVideoClick = onVideoClick)
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

        Text(
            text = "Sources de recherche",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(12.dp))

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

        Text(
            text = "Tendances actuelles",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )

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
                        onClick = { viewModel.fetchDynamicVideos() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E1A47))
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFFE0AAFF))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Réessayer", color = Color(0xFFE0AAFF))
                    }
                }
            }
            is UiState.Success -> {
                state.videos.take(3).forEach { video ->
                    VideoCard(video = video, onClick = { onVideoClick(video) })
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun SearchScreen(viewModel: VideoViewModel, onVideoClick: (VideoItem) -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    var searchTriggered by remember { mutableStateOf(false) }
    val state = viewModel.uiState

    val allVideos = (state as? UiState.Success)?.videos ?: emptyList()

    val filteredList = if (searchQuery.isBlank()) {
        emptyList()
    } else {
        allVideos.filter { 
            it.title.contains(searchQuery, ignoreCase = true) || 
            it.source.contains(searchQuery, ignoreCase = true) 
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Recherche Avancée",
            color = Color(0xFFE0AAFF),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { 
                searchQuery = it
                searchTriggered = it.isNotBlank()
            },
            placeholder = { Text("Tapez un mot-clé, un auteur...", color = Color(0xFF9A8CB9)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFFE0AAFF)) },
            singleLine = true,
            shape = RoundedCornerShape(25.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1F103F),
                unfocusedContainerColor = Color(0xFF1F103F),
                focusedBorderColor = Color(0xFFE0AAFF),
                unfocusedBorderColor = Color(0xFF3B2859),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (!searchTriggered) {
            Text(
                text = "Suggestions populaires",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))

            val historyItems = listOf("Jetpack Compose", "ExoPlayer streaming", "Animation Blender", "Kotlin coroutines")
            historyItems.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { 
                            searchQuery = item
                            searchTriggered = true
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFF7A6B9C),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = item,
                        color = Color(0xFFD4C9EC),
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            Text(
                text = "Résultats (${filteredList.size})",
                color = Color(0xFFE0AAFF),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aucun résultat trouvé",
                        color = Color(0xFF9A8CB9),
                        fontSize = 14.sp
                    )
                }
            } else {
                filteredList.forEach { video ->
                    VideoCard(video = video, onClick = { onVideoClick(video) })
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
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
        SettingsItem(title = "Thème de l'application", subtitle = "Saphir & Améthyste (Actif)")
        SettingsItem(title = "Mode sombre automatique", subtitle = "Activé par défaut")

        Spacer(modifier = Modifier.height(24.dp))

        SettingsSectionTitle("Données et Cache")
        Spacer(modifier = Modifier.height(8.dp))
        SettingsItem(title = "Effacer le cache vidéo", subtitle = "12.4 Mo utilisés")
        SettingsItem(title = "Qualité de lecture par défaut", subtitle = "Automatique (HD)")

        Spacer(modifier = Modifier.height(24.dp))

        SettingsSectionTitle("À propos")
        Spacer(modifier = Modifier.height(8.dp))
        SettingsItem(title = "Version de l'application", subtitle = "AYAM VIDEO v1.3.0")
        SettingsItem(title = "Développé par", subtitle = "AYAM Abdoul Lab")
        
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
fun SettingsItem(title: String, subtitle: String) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF130826)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = Color(0xFF9A8CB9),
                fontSize = 12.sp
            )
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
    val isFavorite = viewModel.isFavorite(video)
    val isDownloaded = viewModel.isDownloaded(video)
    
    var downloadState by remember { mutableStateOf(if(isDownloaded) "Téléchargé" else "Télécharger") }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(Uri.parse(video.videoUrl))
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
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
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Retour",
                        tint = Color(0xFFE0AAFF)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Lecture en cours",
                    color = Color(0xFFE0AAFF),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(
                onClick = { viewModel.toggleFavorite(video) }
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favori",
                    tint = Color(0xFFE0AAFF)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(Color(0xFF130826), shape = RoundedCornerShape(12.dp))
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    color = Color(0xFFE0AAFF),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = video.details,
                    color = Color(0xFF9A8CB9),
                    fontSize = 14.sp
                )
            }
            
            Surface(
                onClick = {
                    if (!isDownloaded) {
                        viewModel.downloadVideo(video)
                        downloadState = "Téléchargé"
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
                        imageVector = if (isDownloadsTag) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = "Télécharger",
                        tint = if (isDownloaded) Color(0xFFE0AAFF) else Color(0xFF7A6B9C)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = downloadState,
                        color = if (isDownloaded) Color.White else Color(0xFF9A8CB9),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun SourceBadge(name: String) {
    var isSelected by remember { mutableStateOf(true) }

    Surface(
        onClick = { isSelected = !isSelected },
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) Color(0xFF2E1A47) else Color(0xFF130826),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isSelected) Color(0xFFE0AAFF) else Color(0xFF3B2859)
        ),
        modifier = Modifier.size(width = 90.dp, height = 75.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Alignment.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isSelected) "✔" else "＋",
                color = if (isSelected) Color(0xFFE0AAFF) else Color(0xFF7A6B9C),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = name,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
