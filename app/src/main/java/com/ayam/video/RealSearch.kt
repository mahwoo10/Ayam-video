package com.ayam.video

import android.content.Context
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException

object SearchHolder {
    val tabState = mutableStateOf(0)
    var query by mutableStateOf("")
    var results by mutableStateOf<List<VideoItem>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var searched by mutableStateOf(false)

    /** null = toutes les sources */
    var source by mutableStateOf<String?>(null)
}

suspend fun runSearch(context: Context) {
    val q = SearchHolder.query.trim()
    if (q.isEmpty()) return
    SearchHolder.loading = true
    SearchHolder.error = null
    SearchHolder.searched = true
    try {
        SearchHolder.results = VideoEngine.search(context, q, 30, SearchHolder.source)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        SearchHolder.results = emptyList()
        SearchHolder.error = "La recherche a échoué. Vérifie ta connexion et réessaie."
    } finally {
        SearchHolder.loading = false
    }
}

fun clearSearch() {
    SearchHolder.query = ""
    SearchHolder.results = emptyList()
    SearchHolder.error = null
    SearchHolder.searched = false
    SearchHolder.loading = false
}

@androidx.compose.runtime.Composable
fun SearchBar(onSearch: () -> Unit, onClear: () -> Unit) {
    OutlinedTextField(
        value = SearchHolder.query,
        onValueChange = { SearchHolder.query = it },
        placeholder = { Text("Rechercher une vidéo ou une musique", color = Pal.muted2) },
        singleLine = true,
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null, tint = Pal.accent)
        },
        trailingIcon = {
            if (SearchHolder.query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, contentDescription = "Effacer", tint = Pal.muted)
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = Pal.accent,
            unfocusedBorderColor = Pal.border,
            cursorColor = Pal.accent
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
