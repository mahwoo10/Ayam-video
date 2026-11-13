package com.ayam.video

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
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
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .background(Pal.surface)
            .border(1.dp, Pal.border, shape)
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = Pal.accent,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (SearchHolder.query.isEmpty()) {
                Text("Rechercher", color = Pal.muted2, fontSize = 14.sp)
            }
            BasicTextField(
                value = SearchHolder.query,
                onValueChange = { SearchHolder.query = it },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                cursorBrush = SolidColor(Pal.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (SearchHolder.query.isNotEmpty()) {
            IconButton(onClick = onClear, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Effacer",
                    tint = Pal.muted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
