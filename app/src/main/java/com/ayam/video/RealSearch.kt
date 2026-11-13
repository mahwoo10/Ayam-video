package com.ayam.video

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

object SearchHolder {
    val tabState = mutableStateOf(0)
    var query by mutableStateOf("")
    var results by mutableStateOf<List<VideoItem>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var searched by mutableStateOf(false)
}

@Composable
fun RealSearchScreen(onVideoClick: (VideoItem) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    fun runSearch() {
        val q = SearchHolder.query.trim()
        if (q.isEmpty() || SearchHolder.loading) return
        focus.clearFocus()
        SearchHolder.loading = true
        SearchHolder.error = null
        SearchHolder.searched = true
        scope.launch {
            try {
                // AUGMENTATION DE LA LIMITE À 30 RÉSULTATS POUR LA RECHERCHE
                SearchHolder.results = VideoEngine.search(context, q, 30)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SearchHolder.results = emptyList()
                SearchHolder.error = "La recherche a échoué. Vérifie ta connexion et réessaie."
            } finally {
                SearchHolder.loading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Recherche",
            color = Pal.accent,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = SearchHolder.query,
            onValueChange = { SearchHolder.query = it },
            placeholder = { Text("Rechercher sur YouTube...", color = Pal.muted2) },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { runSearch() }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Rechercher",
                        tint = Pal.accent
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { runSearch() }),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Pal.accent,
                unfocusedBorderColor = Pal.border,
                cursorColor = Pal.accent
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

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
            !SearchHolder.searched -> {
                Text(
                    text = "Tape un mot-clé puis valide avec la loupe.",
                    color = Pal.muted,
                    fontSize = 14.sp
                )
            }
            SearchHolder.results.isEmpty() -> {
                Text(text = "Aucun résultat trouvé", color = Pal.muted, fontSize = 14.sp)
            }
            else -> {
                Text(
                    text = "Résultats (${SearchHolder.results.size})",
                    color = Pal.accent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                SearchHolder.results.forEach { video ->
                    VideoCard(video = video, onClick = { onVideoClick(video) })
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
