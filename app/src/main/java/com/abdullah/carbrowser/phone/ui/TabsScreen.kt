package com.abdullah.carbrowser.phone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.abdullah.carbrowser.browser.CarBrowser
import com.abdullah.carbrowser.browser.Library
import com.abdullah.carbrowser.browser.Prefs

@Composable
fun TabsScreen(onDone: () -> Unit) {
    watch()
    var section by rememberSaveable { mutableIntStateOf(0) }
    val cs = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = section, containerColor = cs.background, contentColor = cs.primary) {
            listOf("Open tabs", "Bookmarks", "History").forEachIndexed { i, t ->
                Tab(selected = section == i, onClick = { section = i }, text = { Text(t) })
            }
        }
        Box(Modifier.weight(1f)) {
            when (section) {
                0 -> OpenTabs(onDone)
                1 -> Bookmarks(onDone)
                else -> History(onDone)
            }
        }
    }
}

@Composable
private fun OpenTabs(onDone: () -> Unit) {
    watch()
    val tabs = CarBrowser.tabs
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${tabs.size} tab${if (tabs.size == 1) "" else "s"}", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).padding(start = 6.dp))
                    if (tabs.size > 1) TextButton(onClick = { CarBrowser.closeAllTabs() }) { Text("Close all") }
                }
            }
            items(tabs, key = { it.id }) { t ->
                Row1(
                    title = t.title,
                    subtitle = if (t.isHome) "Start page" else Library.hostOf(t.url),
                    selected = t.active,
                    onClick = { CarBrowser.selectTab(t.id); onDone() },
                ) {
                    IconButton(onClick = { CarBrowser.closeTab(t.id) }) { Icon(Icons.Rounded.Close, "Close ${t.title}") }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { CarBrowser.newTab(); onDone() },
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text("New tab") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
private fun Bookmarks(onDone: () -> Unit) {
    watch()
    val list = Library.bookmarks
    if (list.isEmpty()) { Empty("No bookmarks yet. Tap the star next to the address bar to save a page."); return }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(list, key = { it.url }) { b ->
            Row1(b.title.ifBlank { Library.hostOf(b.url) }, Library.hostOf(b.url), false,
                onClick = { CarBrowser.loadUrl(b.url); onDone() }) {
                IconButton(onClick = { CarBrowser.newTab(b.url); onDone() }) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Open ${b.title} in a new tab")
                }
                IconButton(onClick = { Library.removeBookmark(b.url); CarBrowser.settingsChanged() }) {
                    Icon(Icons.Rounded.Delete, "Delete bookmark ${b.title}")
                }
            }
        }
    }
}

@Composable
private fun History(onDone: () -> Unit) {
    watch()
    val list = Library.history
    if (!Prefs.keepHistory && list.isEmpty()) { Empty("History is turned off in Settings."); return }
    if (list.isEmpty()) { Empty("Pages you visit will show up here."); return }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recent", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(start = 6.dp))
                TextButton(onClick = { Library.clearHistory(); CarBrowser.notifyChanged() }) { Text("Clear history") }
            }
        }
        items(list, key = { it.url + it.time }) { h ->
            Row1(h.title.ifBlank { Library.hostOf(h.url) }, Library.hostOf(h.url), false,
                onClick = { CarBrowser.loadUrl(h.url); onDone() }) {}
        }
    }
}

@Composable
private fun Empty(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A tab / bookmark / history row with a letter "favicon". */
@Composable
fun Row1(
    title: String, subtitle: String, selected: Boolean, onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) cs.secondaryContainer else cs.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
            .then(if (selected) Modifier.border(1.5.dp, cs.primary, RoundedCornerShape(18.dp)) else Modifier),
    ) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).background(cs.surfaceContainerHighest, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(subtitle.firstOrNull()?.uppercase() ?: "•", color = cs.primary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            trailing()
        }
    }
}
