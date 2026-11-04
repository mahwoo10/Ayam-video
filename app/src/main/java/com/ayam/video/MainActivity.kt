package com.ayam.video

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF090412)
                ) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun MainScreen() {
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
                0 -> HomeScreen()
                1 -> PlaceholderScreen("Écran de Recherche Avancée")
                2 -> PlaceholderScreen("Écran de la Bibliothèque")
                3 -> PlaceholderScreen("Écran des Paramètres")
            }
        }
    }
}

@Composable
fun HomeScreen() {
    val scrollState = rememberScrollState()

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

        // Barre de recherche
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(Color(0xFF1F103F), shape = RoundedCornerShape(25.dp))
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = "Rechercher une vidéo...",
                color = Color(0xFF9A8CB9),
                fontSize = 14.sp
            )
        }

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

        Text(
            text = "Tendances actuelles",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Liste de vidéos simulées pour le design
        val sampleVideos = listOf(
            VideoItem("Développement Android Jetpack Compose de zéro", "Tutoriels • 12K vues", "YouTube"),
            VideoItem("Créer un lecteur vidéo puissant avec ExoPlayer & Media3", "Code Master • 8.5K vues", "Vimeo"),
            VideoItem("Architecture Moderne MVVM en Kotlin 2026", "Tech Expert • 20K vues", "YouTube")
        )

        sampleVideos.forEach { video ->
            VideoCard(video = video)
            Spacer(modifier = Modifier.height(12.dp))
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

data class VideoItem(val title: String, val details: String, val source: String)

@Composable
fun VideoCard(video: VideoItem) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF130826)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Espace Miniature de la vidéo
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(Color(0xFF1F103F)),
                contentAlignment = Alignment.Center
            ) {
                // Icône de lecture pour simuler le player
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
                
                // Badge de source en haut à droite
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

            // Informations de la vidéo
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
