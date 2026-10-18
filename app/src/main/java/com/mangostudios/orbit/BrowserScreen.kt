package com.mangostudios.orbit

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.max

val Night = Color(0xFF0B0D14)
private val PageRadius = 22.dp

/** Orbit's glass: a translucent fill with a light rim, brighter at the top like the Mac chrome. */
fun Modifier.glass(shape: Shape, tint: Color = Color.Transparent): Modifier = this
    .clip(shape)
    .background(Color.White.copy(alpha = 0.10f), shape)
    .background(tint, shape)
    .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.06f))), shape)

@Composable
fun BrowserScreen(browser: Browser) {
    val tab = browser.selected ?: return
    val hue = tab.glowHue
    val focus = LocalFocusManager.current

    BackHandler(enabled = browser.showingSwitcher || tab.canGoBackHere) {
        if (browser.showingSwitcher) browser.showingSwitcher = false else tab.goBack()
    }

    Box(Modifier.fillMaxSize().background(Night)) {
        AmbientBackdrop(hue)
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 6.dp, top = 4.dp)
            ) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(PageRadius)).background(Night)) {
                    key(tab.id, tab.showingStart) {
                        if (tab.showingStart) {
                            StartPage(tab.isPrivate, onSubmit = { focus.clearFocus(); tab.load(it, browser.context) })
                        } else {
                            WebPage(tab)
                        }
                    }
                    ProgressLine(tab)
                }
                PageGlow(trigger = tab.glowPulse, hue = hue, cornerRadius = PageRadius, modifier = Modifier.testTag("page-glow"))
            }
            Chrome(browser, tab)
        }
        AnimatedVisibility(
            visible = browser.showingSwitcher,
            enter = fadeIn(tween(180)) + scaleIn(initialScale = 1.04f),
            exit = fadeOut(tween(150)) + scaleOut(targetScale = 1.04f),
        ) {
            TabSwitcher(browser)
        }
    }
}

@Composable
private fun WebPage(tab: Tab) {
    AndroidView(
        factory = { ctx ->
            tab.attach(ctx).also { wv ->
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
        },
        modifier = Modifier.fillMaxSize(),
        onRelease = { wv -> (wv.parent as? ViewGroup)?.removeView(wv) },
    )
}

/** A thin line in the page's color across the top while it loads. */
@Composable
private fun ProgressLine(tab: Tab) {
    if (!tab.isLoading || tab.showingStart) return
    val color = Hue.color(tab.glowHue, 0.75)
    Box(
        Modifier
            .fillMaxWidth(max(0.05f, tab.progress / 100f))
            .height(2.5.dp)
            .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.4f), color, Color.White.copy(alpha = 0.9f))))
    )
}

/** Soft light in the page's color behind the chrome, like the Mac's AmbientBackdrop. */
@Composable
private fun AmbientBackdrop(hue: Double) {
    val color by animateColorAsState(Hue.color(hue, 0.8, 1.0), tween(900), label = "ambient")
    Canvas(Modifier.fillMaxSize()) {
        val side = max(size.width, size.height)
        listOf(Offset(0.15f, 0f) to 0.5f, Offset(0.85f, 0.02f) to 0.4f, Offset(0.5f, 1.05f) to 0.45f).forEach { (p, r) ->
            val center = Offset(size.width * p.x, size.height * p.y)
            val radius = side * r
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.22f), Color.Transparent), center, radius), radius, center)
        }
    }
}

@Composable
private fun Chrome(browser: Browser, tab: Tab) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AddressBar(tab) { tab.load(it, context) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            ChromeButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", enabled = tab.canGoBackHere) { tab.goBack() }
            ChromeButton(Icons.AutoMirrored.Rounded.ArrowForward, "Forward", enabled = tab.canGoForwardHere) { tab.goForward() }
            ChromeButton(Icons.Rounded.Add, "New tab") { browser.newTab(isPrivate = tab.isPrivate) }
            TabsButton(browser.tabs.size, tab.isPrivate) { browser.openSwitcher() }
            Box {
                var menu by remember { mutableStateOf(false) }
                ChromeButton(Icons.Rounded.MoreVert, "Menu") { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("New tab") }, leadingIcon = { Icon(Icons.Rounded.Add, null) },
                        onClick = { menu = false; browser.newTab() })
                    DropdownMenuItem(text = { Text("New private tab") }, leadingIcon = { Icon(Icons.Rounded.VisibilityOff, null) },
                        onClick = { menu = false; browser.newTab(isPrivate = true) })
                    DropdownMenuItem(text = { Text("Close tab") }, leadingIcon = { Icon(Icons.Rounded.Close, null) },
                        onClick = { menu = false; browser.close(tab) })
                }
            }
        }
    }
}

@Composable
private fun AddressBar(tab: Tab, onGo: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    val focusRequester = remember { FocusRequester() }
    val focus = LocalFocusManager.current
    val rim by animateColorAsState(
        if (editing) Hue.color(tab.glowHue, 0.6, 1.0, 0.7f) else Color.Transparent, tween(250), label = "rim",
    )
    val shown = if (tab.showingStart) "" else tab.url.orEmpty()

    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .glass(RoundedCornerShape(24.dp), tint = if (tab.isPrivate) Hue.color(Hue.PRIVATE, 0.6, 0.6, 0.25f) else Color.Transparent)
            .border(1.5.dp, rim, RoundedCornerShape(24.dp))
            .clickable(enabled = !editing) { focusRequester.requestFocus() }
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val leading = when {
            tab.isPrivate -> Icons.Rounded.VisibilityOff
            !editing && tab.url?.startsWith("https://") == true && !tab.showingStart -> Icons.Rounded.Lock
            else -> Icons.Rounded.Search
        }
        Icon(leading, null, tint = Color.White.copy(alpha = 0.65f), modifier = Modifier.size(17.dp))
        Spacer(Modifier.size(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (!editing) {
                Text(
                    if (tab.showingStart) "Search or enter address" else (tab.host ?: shown),
                    color = Color.White.copy(alpha = if (tab.showingStart) 0.5f else 0.92f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = field,
                onValueChange = { field = it },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                cursorBrush = SolidColor(Hue.accentLight),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onGo = {
                    if (field.text.isNotBlank()) onGo(field.text)
                    focus.clearFocus()
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (editing) 1f else 0f)
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        editing = it.isFocused
                        if (it.isFocused) field = TextFieldValue(shown, TextRange(0, shown.length))
                    }
                    .testTag("address"),
            )
        }
        when {
            editing && field.text.isNotEmpty() -> ChromeButton(Icons.Rounded.Close, "Clear", size = 36) { field = TextFieldValue("") }
            tab.showingStart -> {}
            tab.isLoading -> ChromeButton(Icons.Rounded.Close, "Stop", size = 36) { tab.stop() }
            else -> ChromeButton(Icons.Rounded.Refresh, "Reload", size = 36) { tab.reload() }
        }
    }
}

@Composable
private fun ChromeButton(icon: ImageVector, label: String, enabled: Boolean = true, size: Int = 44, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = Color.White.copy(alpha = if (enabled) 0.9f else 0.3f), modifier = Modifier.size((size * 0.5f).dp))
    }
}

@Composable
private fun TabsButton(count: Int, private: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClickLabel = "Tabs", onClick = onClick), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(24.dp)
                .border(1.8.dp, if (private) Hue.color(Hue.PRIVATE, 0.55, 1.0) else Color.White.copy(alpha = 0.9f), RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (count > 99) ":D" else "$count", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
