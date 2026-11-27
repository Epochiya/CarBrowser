package com.abdullah.carbrowser.phone.ui

import android.content.Context
import android.media.AudioManager
import android.view.HapticFeedbackConstants
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.abdullah.carbrowser.browser.CarBrowser
import com.abdullah.carbrowser.browser.Library
import com.abdullah.carbrowser.browser.Prefs
import kotlin.math.roundToInt

private fun clock(s: Double): String {
    val t = s.coerceAtLeast(0.0).roundToInt()
    val h = t / 3600; val m = (t % 3600) / 60; val sec = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

@Composable
fun MediaScreen() {
    watch()
    val cs = MaterialTheme.colorScheme
    val view = LocalView.current
    val ctx = LocalContext.current

    // Ask the car screen for live video info while this page is open.
    DisposableEffect(Unit) {
        CarBrowser.mediaWatchers++
        onDispose { CarBrowser.mediaWatchers-- }
    }

    val m = CarBrowser.media
    val step = Prefs.seekStep
    fun tick() { if (Prefs.haptics) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        // ---- now playing
        Column(
            Modifier.fillMaxWidth().background(cs.surfaceContainer, RoundedCornerShape(24.dp)).padding(18.dp)
        ) {
            Text("Now playing", style = MaterialTheme.typography.labelMedium, color = cs.primary)
            Spacer(Modifier.height(4.dp))
            Text(
                CarBrowser.pageTitle.ifBlank { "Nothing playing" },
                style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (!CarBrowser.isHome) Text(Library.hostOf(CarBrowser.pageUrl), style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))

            if (m != null && m.found && m.duration > 0) {
                var dragging by remember { mutableStateOf(false) }
                var pos by remember { mutableFloatStateOf(0f) }
                val shown = if (dragging) pos else m.time.toFloat()
                Slider(
                    value = shown.coerceIn(0f, m.duration.toFloat()),
                    onValueChange = { dragging = true; pos = it },
                    onValueChangeFinished = { CarBrowser.seekTo(pos.toDouble()); dragging = false },
                    valueRange = 0f..m.duration.toFloat(),
                    modifier = Modifier.semantics { contentDescription = "Video position" },
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(clock(shown.toDouble()), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text("-" + clock(m.duration - shown), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
            } else {
                Text(
                    if (!CarBrowser.isConnected) "Connect to the car to control video."
                    else "The player is inside an embedded frame, so there's no timeline here. " +
                        "The buttons still work: they send keyboard keys to the player. " +
                        "If they don't, click the video once with the touchpad first.",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        // ---- transport
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            FilledTonalIconButton(onClick = { tick(); CarBrowser.seek(-step) }, modifier = Modifier.size(68.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.FastRewind, contentDescription = "Back $step seconds")
                    Text("${step}s", style = MaterialTheme.typography.labelSmall)
                }
            }
            val paused = m?.paused ?: true
            FilledIconButton(
                onClick = { tick(); CarBrowser.playPause() },
                modifier = Modifier.size(96.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = cs.primary),
            ) {
                Icon(if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    contentDescription = if (paused) "Play" else "Pause", modifier = Modifier.size(48.dp))
            }
            FilledTonalIconButton(onClick = { tick(); CarBrowser.seek(step) }, modifier = Modifier.size(68.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.FastForward, contentDescription = "Forward $step seconds")
                    Text("${step}s", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        FilledTonalButton(
            onClick = { tick(); CarBrowser.seek(Prefs.skipIntro) },
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Icon(Icons.Rounded.SkipNext, null)
            Spacer(Modifier.width(8.dp))
            Text("Skip intro  (+${Prefs.skipIntro}s)", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val muted = m?.muted ?: false
            OutlinedButton(onClick = { tick(); CarBrowser.toggleMute() }, modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(16.dp)) {
                Icon(if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, null)
                Spacer(Modifier.width(6.dp)); Text(if (muted) "Unmute" else "Mute")
            }
            val theater = CarBrowser.theater
            OutlinedButton(onClick = { tick(); CarBrowser.toggleTheater() }, modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(16.dp)) {
                Icon(if (theater) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, null)
                Spacer(Modifier.width(6.dp)); Text(if (theater) "Exit theater" else "Theater")
            }
        }
        if (CarBrowser.fullscreen) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { CarBrowser.exitFullscreen() }, modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)) { Text("Exit fullscreen") }
        }

        // ---- speed
        SectionLabel("Playback speed", Modifier.fillMaxWidth().padding(start = 0.dp))
        val rate = m?.rate ?: 1.0
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { r ->
                FilterChip(
                    selected = kotlin.math.abs(rate - r) < 0.01,
                    onClick = { tick(); CarBrowser.setSpeed(r) },
                    label = { Text(if (r == 1f) "1×" else "${r}×") },
                )
            }
        }

        // ---- phone volume (audio comes from the phone through the car)
        SectionLabel("Volume", Modifier.fillMaxWidth())
        val am = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
        val max = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat() }
        var vol by remember { mutableFloatStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, tint = cs.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Slider(
                value = vol, valueRange = 0f..max, steps = (max.toInt() - 1).coerceAtLeast(0),
                onValueChange = { vol = it; am.setStreamVolume(AudioManager.STREAM_MUSIC, it.roundToInt(), 0) },
                modifier = Modifier.weight(1f).semantics { contentDescription = "Volume" },
            )
        }
        Spacer(Modifier.height(24.dp))
        Box(Modifier.height(8.dp))
    }
}
