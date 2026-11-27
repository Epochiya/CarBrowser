package com.abdullah.carbrowser.phone.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abdullah.carbrowser.browser.CarBrowser
import com.abdullah.carbrowser.browser.Library
import com.abdullah.carbrowser.browser.Prefs
import com.abdullah.carbrowser.privacy.FilterLists
import com.abdullah.carbrowser.privacy.Privacy
import kotlin.math.roundToInt

private fun changed() = CarBrowser.settingsChanged()

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() {
    watch()
    val cs = MaterialTheme.colorScheme
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var editHome by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 20.dp, top = 16.dp))

        // ------------------------------------------------------------ look
        SectionLabel("Appearance")
        Card {
            Text("Accent colour", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Prefs.ACCENTS.forEach { c ->
                    val sel = Prefs.accent == c
                    Box(
                        Modifier.size(42.dp).background(Color(c), CircleShape)
                            .then(if (sel) Modifier.border(3.dp, cs.onSurface, CircleShape) else Modifier)
                            .clickable(role = Role.RadioButton) { Prefs.accent = c; changed() }
                            .semantics { contentDescription = "Accent colour"; selected = sel },
                        contentAlignment = Alignment.Center,
                    ) { if (sel) Icon(Icons.Rounded.Check, null, tint = Color(0xFF111216)) }
                }
            }
        }
        Card {
            SwitchRow("Pure black", "AMOLED black on the phone and the car screen", Prefs.amoled) { Prefs.amoled = it; changed() }
            SwitchRow("Toolbar on the car screen", "Address bar, back, reload, shields count", Prefs.carToolbar) { Prefs.carToolbar = it; changed() }
            SwitchRow("Tab strip on the car screen", "Shows your tabs above the toolbar when you have more than one", Prefs.carTabStrip) {
                Prefs.carTabStrip = it; changed()
            }
            SliderRow("Text size on the car", Prefs.carTextZoom.toFloat(), 70f..200f, 25, { "${it.roundToInt()}%" }) {
                Prefs.carTextZoom = (it / 5).roundToInt() * 5; changed()
            }
            SliderRow("Cursor size", Prefs.cursorSize, 0.6f..2.2f, 15, { "%.1f×".format(it) }) { Prefs.cursorSize = it; changed() }
        }

        // ------------------------------------------------------------ touchpad
        SectionLabel("Touchpad")
        Card {
            SliderRow("Pointer speed", Prefs.sensitivity, 0.3f..3f, 26, { "%.1f×".format(it) }) { Prefs.sensitivity = it; changed() }
            SwitchRow("Pointer acceleration", "Fast swipes travel further, slow ones stay precise", Prefs.acceleration) {
                Prefs.acceleration = it; changed()
            }
            SliderRow("Scroll speed", Prefs.scrollSpeed, 0.3f..3f, 26, { "%.1f×".format(it) }) { Prefs.scrollSpeed = it; changed() }
            SwitchRow("Natural scrolling", "The page follows your fingers, like a phone", Prefs.naturalScroll) { Prefs.naturalScroll = it; changed() }
            SwitchRow("Tap to click", "Off = only the click buttons click", Prefs.tapToClick) { Prefs.tapToClick = it; changed() }
            SwitchRow("Click buttons", "Big left / right click buttons under the touchpad", Prefs.clickButtons) { Prefs.clickButtons = it; changed() }
            SwitchRow("Scroll wheel", "A strip beside the touchpad for one-finger scrolling", Prefs.scrollStrip) { Prefs.scrollStrip = it; changed() }
            SwitchRow("Vibration", "Little buzz on clicks and scroll ticks", Prefs.haptics) { Prefs.haptics = it; changed() }
            SwitchRow("Gesture hints", "Show the help text on the touchpad", Prefs.showHints) { Prefs.showHints = it; changed() }
        }

        // ------------------------------------------------------------ media
        SectionLabel("Media")
        Card {
            Text("Seek buttons jump", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 15, 30).forEach { s ->
                    FilterChip(selected = Prefs.seekStep == s, onClick = { Prefs.seekStep = s; changed() }, label = { Text("${s}s") })
                }
            }
            Spacer(Modifier.height(8.dp))
            SliderRow("Skip intro jumps", Prefs.skipIntro.toFloat(), 30f..150f, 23, { "${it.roundToInt()}s" }) {
                Prefs.skipIntro = (it / 5).roundToInt() * 5; changed()
            }
        }

        // ------------------------------------------------------------ browsing
        SectionLabel("Browsing")
        Card {
            Text("Search engine", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Prefs.Engine.entries.forEach { e ->
                    FilterChip(selected = Prefs.engine == e, onClick = { Prefs.engine = e; changed() }, label = { Text(e.label) })
                }
            }
            Spacer(Modifier.height(4.dp))
            ClickRow("Home page", if (Prefs.homePage == Prefs.HOME) "Start page" else Prefs.homePage) { editHome = true }
            SwitchRow("Desktop sites", "Ask websites for their computer version", Prefs.desktopMode) { Prefs.desktopMode = it; changed() }
            SwitchRow("Keep history", "Saved on this phone only", Prefs.keepHistory) {
                Prefs.keepHistory = it; if (!it) Library.clearHistory(); changed()
            }
        }

        // ------------------------------------------------------------ privacy
        SectionLabel("Privacy & shields")
        Card {
            SwitchRow("Shields", "Block ads, trackers, pop-ups and redirects you didn't click", Prefs.shields) { CarBrowser.setShields(it) }
            SwitchRow(
                "Allow cookies for this visit",
                "Off = refuse every cookie. On = first-party only, wiped when you disconnect. Turn on if a site won't log in.",
                Prefs.cookies == Privacy.Cookies.SESSION,
            ) {
                Prefs.cookies = if (it) Privacy.Cookies.SESSION else Privacy.Cookies.OFF
                changed(); CarBrowser.reload()
            }
            SwitchRow("Wipe site data on disconnect", "Cookies, storage and cache are erased when you unplug", Prefs.wipeOnDisconnect) {
                Prefs.wipeOnDisconnect = it; changed()
            }
            val updated = FilterLists.lastUpdated.takeIf { it > 0 }
                ?.let { DateUtils.getRelativeTimeSpanString(it).toString() } ?: "not downloaded yet"
            Spacer(Modifier.height(8.dp))
            Text(
                if (FilterLists.updating) "Updating filter lists…"
                else "%,d blocking rules · lists updated %s".format(FilterLists.engine.ruleCount, updated),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { FilterLists.updateNow(ctx) }, enabled = !FilterLists.updating, shape = RoundedCornerShape(14.dp)) {
                    Text("Update lists")
                }
                OutlinedButton(onClick = { CarBrowser.clearData() }, shape = RoundedCornerShape(14.dp)) { Text("Clear site data") }
            }
        }

        // ------------------------------------------------------------ help
        SectionLabel("Car screen setup")
        Card {
            Text(
                "1. Android Auto settings → tap Version 10 times to unlock developer mode.\n" +
                    "2. ⋮ → Developer settings → turn on Unknown sources.\n" +
                    "3. Force-stop Android Auto, then reconnect to the car.\n" +
                    "4. Customise launcher → make sure Car Browser is ticked.",
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
            )
        }
        Text(
            "Car Browser " + runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrDefault(""),
            style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
            modifier = Modifier.padding(20.dp),
        )
    }

    if (editHome) {
        var text by remember { mutableStateOf(if (Prefs.homePage == Prefs.HOME) "" else Prefs.homePage) }
        AlertDialog(
            onDismissRequest = { editHome = false },
            title = { Text("Home page") },
            text = {
                OutlinedTextField(text, { text = it }, singleLine = true, placeholder = { Text("Leave empty for the start page") })
            },
            confirmButton = {
                TextButton(onClick = {
                    Prefs.homePage = if (text.isBlank()) Prefs.HOME else Prefs.normalize(text)
                    editHome = false; changed()
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { Prefs.homePage = Prefs.HOME; editHome = false; changed() }) { Text("Use start page") } },
        )
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) { content() }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ClickRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1,
            modifier = Modifier.padding(start = 12.dp))
    }
}

/** Slider that only saves when you let go (so the car screen doesn't redraw on every pixel). */
@Composable
private fun SliderRow(
    title: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int,
    label: (Float) -> String, onCommit: (Float) -> Unit,
) {
    var v by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(label(v), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = v, onValueChange = { v = it }, onValueChangeFinished = { onCommit(v) },
            valueRange = range, steps = steps,
            modifier = Modifier.semantics { contentDescription = title },
        )
    }
}

