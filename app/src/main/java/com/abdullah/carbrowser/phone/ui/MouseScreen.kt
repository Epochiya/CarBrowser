package com.abdullah.carbrowser.phone.ui

import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.automirrored.rounded.KeyboardTab
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardHide
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.abdullah.carbrowser.browser.CarBrowser
import com.abdullah.carbrowser.browser.Prefs
import com.abdullah.carbrowser.phone.TouchpadView

@Composable
fun MouseScreen() {
    watch()
    var keyboard by rememberSaveable { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 12.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(Modifier.weight(1f).fillMaxWidth()) {
            val pad = cs.surfaceContainer.toArgb()
            val dots = cs.surfaceContainerHighest.toArgb()
            val accent = cs.primary.toArgb()
            val hint = cs.onSurfaceVariant.copy(alpha = 0.75f).toArgb()
            val hints = Prefs.showHints
            AndroidView(
                factory = { TouchpadView(it) },
                update = { v ->
                    v.padColor = pad; v.dotColor = dots; v.accentColor = accent; v.hintColor = hint
                    v.showHints = hints
                    v.invalidate()
                },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            if (Prefs.scrollStrip) {
                Spacer(Modifier.width(8.dp))
                ScrollStrip(Modifier.width(44.dp).fillMaxHeight())
            }
        }

        if (Prefs.clickButtons) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().height(58.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClickButton("Left click", Modifier.weight(1f)) { CarBrowser.click() }
                ClickButton("Right click", Modifier.weight(1f)) { CarBrowser.longPress() }
            }
        }

        AnimatedVisibility(keyboard, enter = expandVertically(), exit = shrinkVertically()) {
            KeyboardPanel(onClose = { keyboard = false })
        }

        Spacer(Modifier.height(8.dp))
        QuickBar(keyboard = keyboard, onKeyboard = { keyboard = !keyboard })
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ClickButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    val view = LocalView.current
    Surface(
        onClick = {
            if (Prefs.haptics) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onClick()
        },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxHeight(),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Remote Mouse's scroll wheel: drag up and down on the strip to scroll the page. */
@Composable
private fun ScrollStrip(modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val view = LocalView.current
    val density = view.resources.displayMetrics.density
    var acc = remember { floatArrayOf(0f) }
    Box(
        modifier
            .background(cs.surfaceContainer, RoundedCornerShape(22.dp))
            .semantics { contentDescription = "Scroll wheel. Drag up or down to scroll the page." }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dy ->
                    change.consume()
                    val k = 2.6f * Prefs.scrollSpeed / density * (if (Prefs.naturalScroll) 1f else -1f)
                    CarBrowser.scroll(0f, dy * k)
                    acc[0] += dy
                    if (Prefs.haptics && kotlin.math.abs(acc[0]) > 28 * density) {
                        acc[0] = 0f
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.UnfoldMore, null, tint = cs.onSurfaceVariant)
    }
}

@Composable
private fun QuickBar(keyboard: Boolean, onKeyboard: () -> Unit) {
    watch()
    var more by remember { mutableStateOf(false) }
    val paused = CarBrowser.media?.paused ?: true
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Quick(Icons.AutoMirrored.Rounded.ArrowBack, "Back", enabled = CarBrowser.isConnected) { CarBrowser.back() }
        Quick(Icons.Rounded.Refresh, "Reload") { CarBrowser.reload() }
        Quick(if (keyboard) Icons.Rounded.KeyboardHide else Icons.Rounded.Keyboard,
            if (keyboard) "Hide keyboard" else "Type into the page", highlighted = keyboard) { onKeyboard() }
        Quick(if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, "Play or pause") { CarBrowser.playPause() }
        Quick(Icons.Rounded.Fullscreen, "Theater mode: video fills the car screen", highlighted = CarBrowser.theater) {
            CarBrowser.toggleTheater()
        }
        Box {
            Quick(Icons.Rounded.MoreHoriz, "More") { more = true }
            DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                DropdownMenuItem(text = { Text("Forward") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.ArrowForward, null) },
                    onClick = { CarBrowser.forward(); more = false })
                DropdownMenuItem(text = { Text("Home") }, leadingIcon = { Icon(Icons.Rounded.Home, null) },
                    onClick = { CarBrowser.home(); more = false })
                DropdownMenuItem(text = { Text("Zoom in") }, leadingIcon = { Icon(Icons.Rounded.ZoomIn, null) },
                    onClick = { CarBrowser.zoom(true) })
                DropdownMenuItem(text = { Text("Zoom out") }, leadingIcon = { Icon(Icons.Rounded.ZoomOut, null) },
                    onClick = { CarBrowser.zoom(false) })
                DropdownMenuItem(
                    text = { Text(if (Prefs.desktopMode) "Mobile site" else "Desktop site") },
                    leadingIcon = { Icon(Icons.Rounded.DesktopWindows, null) },
                    onClick = { Prefs.desktopMode = !Prefs.desktopMode; CarBrowser.settingsChanged(); more = false })
                if (CarBrowser.fullscreen) DropdownMenuItem(text = { Text("Exit fullscreen") },
                    leadingIcon = { Icon(Icons.Rounded.Fullscreen, null) },
                    onClick = { CarBrowser.exitFullscreen(); more = false })
            }
        }
    }
}

@Composable
private fun Quick(icon: ImageVector, label: String, enabled: Boolean = true, highlighted: Boolean = false, onClick: () -> Unit) {
    val view = LocalView.current
    val cs = MaterialTheme.colorScheme
    FilledTonalIconButton(
        onClick = {
            if (Prefs.haptics) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onClick()
        },
        enabled = enabled,
        modifier = Modifier.size(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (highlighted) cs.primary else cs.surfaceContainerHigh,
            contentColor = if (highlighted) cs.onPrimary else cs.onSurface,
        ),
    ) { Icon(icon, contentDescription = label) }
}

/**
 * Live keyboard: every letter you type appears on the car screen straight away (like Remote Mouse).
 * Tap a text box on the car screen first so the page knows where to put it.
 */
@Composable
private fun KeyboardPanel(onClose: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val cs = MaterialTheme.colorScheme
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(
        Modifier.padding(top = 8.dp).fillMaxWidth()
            .background(cs.surfaceContainer, RoundedCornerShape(20.dp)).padding(10.dp)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { new ->
                when {
                    new.startsWith(text) -> CarBrowser.typeText(new.substring(text.length))
                    text.startsWith(new) -> repeat(text.length - new.length) { CarBrowser.backspace() }
                    else -> { repeat(text.length) { CarBrowser.backspace() }; CarBrowser.typeText(new) }
                }
                text = new
            },
            placeholder = { Text("Types live into the selected box") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onSend = { CarBrowser.enter(); text = "" }),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = cs.outlineVariant),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Key(Icons.AutoMirrored.Rounded.KeyboardTab, "Tab") { CarBrowser.key(KeyEvent.KEYCODE_TAB) }
            Key(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Left arrow") { CarBrowser.key(KeyEvent.KEYCODE_DPAD_LEFT) }
            Key(Icons.Rounded.KeyboardArrowUp, "Up arrow") { CarBrowser.key(KeyEvent.KEYCODE_DPAD_UP) }
            Key(Icons.Rounded.KeyboardArrowDown, "Down arrow") { CarBrowser.key(KeyEvent.KEYCODE_DPAD_DOWN) }
            Key(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Right arrow") { CarBrowser.key(KeyEvent.KEYCODE_DPAD_RIGHT) }
            Key(Icons.AutoMirrored.Rounded.Backspace, "Backspace") {
                CarBrowser.backspace(); if (text.isNotEmpty()) text = text.dropLast(1)
            }
            Key(Icons.AutoMirrored.Rounded.KeyboardReturn, "Enter") { CarBrowser.enter(); text = "" }
        }
    }
}

@Composable
private fun Key(icon: ImageVector, label: String, onClick: () -> Unit) {
    val view = LocalView.current
    FilledTonalIconButton(
        onClick = { if (Prefs.haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); onClick() },
        modifier = Modifier.size(44.dp),
        shape = RoundedCornerShape(12.dp),
    ) { Icon(icon, contentDescription = label) }
}
