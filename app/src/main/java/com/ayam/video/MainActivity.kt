package com.ayam.video

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF090412)
                ) {
                    MainAppRoot()
                }
            }
        }
    }
}

@Composable
fun MainAppRoot() {
    var currentVideo by remember { mutableStateOf<VideoItem?>(null) }

    if (currentVideo != null) {
        PlayerScreen(video = currentVideo!!) {
            currentVideo = null
        }
    } else {
        MainScreen(onVideoClick = { video ->
            currentVideo = video
        })
    }
}

@Composable
fun MainScreen(onVideoClick: (VideoItem) -> Unit) {
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
                0 -> HomeScreen(onVideoClick = onVideoClick)
                1 -> PlaceholderScreen("Écran de Recherche Avancée")
                2 -> PlaceholderScreen("Écran de la Bibliothèque")
                3 -> PlaceholderScreen("Écran des Paramètres")
            }
        }
    }
}

@Composable
fun HomeScreen(onVideoClick: (VideoItem) -> Unit) {
    val scrollState = rememberScrollState()
    var searchQuery by remember { mutableStateOf("") }
    var activeSearchKeyword by remember { mutableStateOf("") }

    // Base de données de démonstration enrichie pour tester la recherche dynamique
    val allVideos = listOf(
        VideoItem(
            "Big Buck Bunny (Animation Test)", 
            "Blender Foundation • 60 FPS", 
            "YouTube",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        ),
        VideoItem(
            "Elephant's Dream (Open Movie)", 
            "Blender Project • HD", 
            "Vimeo",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"
        ),
        VideoItem(
            "For Bigger Blazes (Trailer)", 
            "Google Samples • 4K", 
            "Dailymotion",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"
        ),
        VideoItem(
            "Jetpack Compose Tutoriel Avancé", 
            "Android Dev • 15K vues", 
            "YouTube",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"
        )
    )

    // Filtrage dynamique des vidéos selon le mot-clé validé
    val displayedVideos = if (activeSearchKeyword.isBlank()) {
        allVideos
    } else {
        allVideos.filter { 
            it.title.contains(activeSearchKeyword, ignoreCase = true) || 
            it.source.contains(activeSearchKeyword, ignoreCase = true) 
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

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Rechercher une vidéo...", color = Color(0xFF9A8CB9)) },
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
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    activeSearchKeyword = searchQuery
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

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

        val sectionTitle = if (activeSearchKeyword.isNotEmpty()) {
            "Résultats pour : \"$activeSearchKeyword\" (${displayedVideos.size})"
        } else {
            "Tendances actuelles"
        }

        Text(
            text = sectionTitle,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (displayedVideos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Aucune vidéo trouvée pour cette recherche",
                    color = Color(0xFF9A8CB9),
                    fontSize = 14.sp
                )
            }
        } else {
            displayedVideos.forEach { video ->
                VideoCard(video = video, onClick = { onVideoClick(video) })
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

data class VideoItem(
    val title: String, 
    val details: String, 
    val source: String,
    val videoUrl: String
)

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
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF2E1A47),
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
fun PlayerScreen(video: VideoItem, onBack: () -> Unit) {
    val context = LocalContext.current

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
            verticalAlignment = Alignment.CenterVertically
        ) {
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

        Text(
            text = video.title,
            color = Color.White,
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
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if,
                color = Color(0xFFE0AAFF),
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

@Composable
fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = Color(0xFFE0AAFF),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

