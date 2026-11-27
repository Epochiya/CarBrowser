package com.abdullah.carbrowser.phone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.abdullah.carbrowser.browser.CarBrowser
import com.abdullah.carbrowser.browser.Library
import com.abdullah.carbrowser.browser.Prefs

/** Full-screen address bar: type a URL or search, with suggestions from bookmarks and history. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchDialog(onDismiss: () -> Unit) {
    val start = if (CarBrowser.isHome) "" else CarBrowser.pageUrl
    var field by remember { mutableStateOf(TextFieldValue(start, TextRange(0, start.length))) }
    var newTab by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val cs = MaterialTheme.colorScheme
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun open(input: String) {
        if (input.isBlank()) return
        if (newTab) CarBrowser.newTab(input) else CarBrowser.loadUrl(input)
        onDismiss()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        RemoteTheme {
            Surface(color = cs.background, modifier = Modifier.fillMaxSize()) {
                Column(Modifier.safeDrawingPadding().imePadding()) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close") }
                        TextField(
                            value = field,
                            onValueChange = { field = it },
                            placeholder = { Text("Search ${Prefs.engine.label} or type a URL") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Rounded.Search, null) },
                            trailingIcon = {
                                if (field.text.isNotEmpty()) IconButton(onClick = { field = TextFieldValue("") }) {
                                    Icon(Icons.Rounded.Close, "Clear")
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { open(field.text) }),
                            shape = RoundedCornerShape(18.dp),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                                focusedContainerColor = cs.surfaceContainerHigh, unfocusedContainerColor = cs.surfaceContainerHigh,
                            ),
                            modifier = Modifier.weight(1f).focusRequester(focus),
                        )
                    }
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !newTab, onClick = { newTab = false }, label = { Text("This tab") })
                        FilterChip(selected = newTab, onClick = { newTab = true }, label = { Text("New tab") })
                    }

                    val q = field.text
                    val showSuggestions = q.isNotBlank() && q != start
                    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (showSuggestions) {
                            item {
                                val target = Prefs.normalize(q)
                                val isSearch = target.startsWith(Prefs.engine.template.substringBefore("%s"))
                                Suggestion(
                                    if (isSearch) Icons.Rounded.Search else Icons.Rounded.Language,
                                    if (isSearch) "Search “$q”" else q,
                                    if (isSearch) Prefs.engine.label else "Go to site",
                                ) { open(q) }
                            }
                            items(Library.suggest(q)) { e ->
                                Suggestion(
                                    if (Library.isBookmarked(e.url)) Icons.Rounded.Star else Icons.Rounded.History,
                                    e.title.ifBlank { Library.hostOf(e.url) }, e.url,
                                ) { open(e.url) }
                            }
                        } else {
                            val marks = Library.bookmarks
                            if (marks.isNotEmpty()) item {
                                SectionLabel("Bookmarks", Modifier.padding(start = 0.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(horizontal = 4.dp)) {
                                    marks.forEach { b ->
                                        Surface(onClick = { open(b.url) }, shape = RoundedCornerShape(16.dp), color = cs.surfaceContainer) {
                                            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Box(Modifier.size(24.dp).background(cs.surfaceContainerHighest, RoundedCornerShape(7.dp)),
                                                    contentAlignment = Alignment.Center) {
                                                    Text(Library.hostOf(b.url).firstOrNull()?.uppercase() ?: "•",
                                                        color = cs.primary, style = MaterialTheme.typography.labelMedium)
                                                }
                                                Spacer(Modifier.width(8.dp))
                                                Text(b.title.ifBlank { Library.hostOf(b.url) }, maxLines = 1)
                                            }
                                        }
                                    }
                                }
                            }
                            val recent = Library.history.take(8)
                            if (recent.isNotEmpty()) {
                                item { SectionLabel("Recent", Modifier.padding(start = 0.dp)) }
                                items(recent) { e ->
                                    Suggestion(Icons.Rounded.History, e.title.ifBlank { Library.hostOf(e.url) }, Library.hostOf(e.url)) { open(e.url) }
                                }
                            }
                            item { Spacer(Modifier.height(24.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Suggestion(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp), color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
