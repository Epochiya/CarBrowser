package com.abdullah.carbrowser.phone.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.abdullah.carbrowser.browser.CarBrowser
import com.abdullah.carbrowser.browser.DrivingLock
import com.abdullah.carbrowser.browser.Library

enum class Page(val label: String, val icon: ImageVector) {
    MOUSE("Mouse", Icons.Rounded.Mouse),
    MEDIA("Media", Icons.Rounded.PlayCircle),
    TABS("Tabs", Icons.Rounded.Tab),
    SETTINGS("Settings", Icons.Rounded.Settings),
}

@Composable
fun RemoteApp() = RemoteTheme {
    var page by rememberSaveable { mutableStateOf(Page.MOUSE) }
    var searching by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { if (page != Page.SETTINGS) AddressBar(onOpen = { searching = true }) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
                Page.entries.forEach { p ->
                    NavigationBarItem(
                        selected = page == p,
                        onClick = { page = p },
                        icon = { Icon(p.icon, contentDescription = null) },
                        label = { Text(p.label) },
                    )
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (page != Page.SETTINGS) {
                StatusBanner()
                NoticeBanner()
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (page) {
                    Page.MOUSE -> MouseScreen()
                    Page.MEDIA -> MediaScreen()
                    Page.TABS -> TabsScreen(onDone = { page = Page.MOUSE })
                    Page.SETTINGS -> SettingsScreen()
                }
            }
        }
    }

    if (searching) SearchDialog(onDismiss = { searching = false })
}

/** Helium-style address pill: what's on the car screen, tap to search or type a URL. */
@Composable
private fun AddressBar(onOpen: () -> Unit) {
    watch()
    val connected = CarBrowser.isConnected
    val url = CarBrowser.pageUrl
    val home = CarBrowser.isHome
    val title = CarBrowser.pageTitle.ifBlank { if (home) "New tab" else Library.hostOf(url) }
    val host = if (home) "Search or type a URL" else Library.hostOf(url)
    val secure = home || url.startsWith("https://")
    val bookmarked = !home && Library.isBookmarked(url)
    val cs = MaterialTheme.colorScheme

    Column(Modifier.fillMaxWidth().background(cs.background).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = onOpen,
                shape = RoundedCornerShape(18.dp),
                color = cs.surfaceContainerHigh,
                modifier = Modifier.weight(1f).height(56.dp)
                    .semantics { contentDescription = "Address bar: $title. Tap to search or enter a website." },
            ) {
                Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(10.dp).clip(CircleShape)
                            .background(if (connected) cs.primary else cs.onSurfaceVariant.copy(alpha = 0.5f))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (home) Icons.Rounded.Search else if (secure) Icons.Rounded.Lock else Icons.Rounded.Warning,
                                contentDescription = if (secure) "Secure" else "Not secure",
                                tint = if (secure) cs.onSurfaceVariant else Color(0xFFFFB86B),
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(host, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    val blocked = CarBrowser.blockedCount
                    if (blocked > 0) {
                        Row(
                            Modifier.clip(RoundedCornerShape(10.dp)).background(cs.primaryContainer)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .semantics { contentDescription = "$blocked ads and trackers blocked" },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Shield, null, tint = cs.primary, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("$blocked", style = MaterialTheme.typography.labelMedium, color = cs.onPrimaryContainer)
                        }
                    }
                }
            }
            IconButton(onClick = { CarBrowser.toggleBookmark() }, enabled = !home) {
                Icon(
                    if (bookmarked) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = if (bookmarked) "Remove bookmark" else "Bookmark this page",
                    tint = if (bookmarked) cs.primary else cs.onSurfaceVariant,
                )
            }
        }
        val p = CarBrowser.progress
        if (connected && p in 0..99) {
            LinearProgressIndicator(
                progress = { p / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = cs.primary,
                trackColor = Color.Transparent,
            )
        } else Spacer(Modifier.height(2.dp))
    }
}

/** Connection / driving state, only when there's something to say. */
@Composable
private fun StatusBanner() {
    watch()
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val noGps = ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
    val (text, warn) = when {
        !CarBrowser.isConnected -> "Not on the car screen yet. Open Car Browser from the Android Auto app launcher. Anything you open here will be waiting there." to false
        CarBrowser.lockState == DrivingLock.State.MOVING -> "Car is moving: video is paused on the car screen. Audio keeps playing." to true
        CarBrowser.lockState == DrivingLock.State.UNKNOWN && noGps -> "Allow location so the parked-only lock works." to true
        else -> null to false
    }
    AnimatedVisibility(text != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (warn) Color(0xFF2E2414) else cs.surfaceContainer)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (warn) Icons.Rounded.Warning else Icons.Rounded.Info, null,
                tint = if (warn) Color(0xFFFFB86B) else cs.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(text ?: "", style = MaterialTheme.typography.bodySmall, color = cs.onSurface)
        }
    }
}

/** Something Shields blocked that you might want to allow. */
@Composable
private fun NoticeBanner() {
    watch()
    val n = CarBrowser.notice
    val cs = MaterialTheme.colorScheme
    AnimatedVisibility(n != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainerHigh)
                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Rounded.Shield, null, tint = cs.primary, modifier = Modifier.size(18.dp))
            Text(n?.text ?: "", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(start = 6.dp),
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (n?.url != null) TextButton(onClick = { CarBrowser.openAnyway() }) { Text("Open anyway", fontWeight = FontWeight.SemiBold) }
            TextButton(onClick = { CarBrowser.dismissNotice() }) { Text("Dismiss") }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 6.dp),
    )
}
