package com.mangostudios.orbit

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Every open tab as a card. Tap to switch, × to close. Private tabs wear violet. */
@Composable
fun TabSwitcher(browser: Browser) {
    Box(Modifier.fillMaxSize().background(Night.copy(alpha = 0.97f))) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (browser.tabs.size == 1) "1 tab" else "${browser.tabs.size} tabs",
                    color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f).testTag("tab-grid"),
            ) {
                items(browser.tabs, key = { it.id }) { tab ->
                    TabCard(tab, selected = tab == browser.selected, onOpen = { browser.select(tab) }, onClose = { browser.close(tab) })
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SwitcherButton("New tab", Icons.Rounded.Add, Hue.accent, Modifier.weight(1f)) { browser.newTab() }
                SwitcherButton("Private", Icons.Rounded.VisibilityOff, Hue.color(Hue.PRIVATE, 0.6, 0.9), Modifier.weight(1f)) {
                    browser.newTab(isPrivate = true)
                }
                SwitcherButton("Done", null, Color.White.copy(alpha = 0.12f), Modifier.weight(0.8f)) { browser.showingSwitcher = false }
            }
        }
    }
}

@Composable
private fun TabCard(tab: Tab, selected: Boolean, onOpen: () -> Unit, onClose: () -> Unit) {
    val hue = tab.glowHue
    val shape = RoundedCornerShape(18.dp)
    val rim = when {
        selected -> Hue.color(hue, 0.7, 1.0)
        tab.isPrivate -> Hue.color(Hue.PRIVATE, 0.6, 1.0, 0.6f)
        else -> Color.White.copy(alpha = 0.12f)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .clip(shape)
            .background(Color(0xFF151824))
            .border(if (selected) 2.dp else 1.dp, rim, shape)
            .clickable(onClickLabel = "Switch to ${tab.displayTitle}", onClick = onOpen)
    ) {
        Row(
            Modifier.fillMaxWidth().background(Hue.color(hue, 0.6, 0.5, 0.55f)).padding(start = 10.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (tab.isPrivate) {
                Icon(Icons.Rounded.VisibilityOff, "Private", tint = Color.White, modifier = Modifier.size(13.dp))
                Spacer(Modifier.size(5.dp))
            } else {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Hue.color(hue, 0.7, 1.0)))
                Spacer(Modifier.size(6.dp))
            }
            Text(
                tab.displayTitle, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.size(32.dp).clip(CircleShape).clickable(onClickLabel = "Close ${tab.displayTitle}", onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, "Close tab", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            val thumb = tab.thumbnail
            if (thumb != null && !tab.showingStart) {
                Image(thumb, null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
            } else {
                // A tiny start page: the accent aurora and the tab's host or name.
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(Hue.color(hue, 0.7, 0.55), Hue.color(hue + 0.08, 0.6, 0.2)))
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (tab.showingStart) "Orbit" else (tab.host ?: ""),
                        color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, fontWeight = FontWeight.Light,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitcherButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, fill: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(fill)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
        }
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
