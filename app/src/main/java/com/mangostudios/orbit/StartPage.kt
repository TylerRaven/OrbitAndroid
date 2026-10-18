package com.mangostudios.orbit

import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

data class QuickLink(val title: String, val url: String) {
    val host: String get() = Uri.parse(url).host ?: url
}

val defaultLinks = listOf(
    QuickLink("YouTube", "https://www.youtube.com"),
    QuickLink("Gmail", "https://mail.google.com"),
    QuickLink("GitHub", "https://github.com"),
    QuickLink("Reddit", "https://www.reddit.com"),
    QuickLink("Spotify", "https://open.spotify.com"),
    QuickLink("Discord", "https://discord.com/app"),
)

/** Orbit's new-tab page: an aurora in the accent color, a clock, a search field and quick links. */
@Composable
fun StartPage(isPrivate: Boolean, onSubmit: (String) -> Unit, modifier: Modifier = Modifier) {
    val hue = if (isPrivate) Hue.PRIVATE else Hue.ACCENT
    Box(modifier.fillMaxSize().background(Color(0xFF07080E))) {
        Aurora(hue)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            if (isPrivate) PrivateNote()
            Clock()
            SearchField(onSubmit)
            Links(onSubmit)
        }
    }
}

@Composable
private fun Aurora(hue: Double) {
    val t by rememberInfiniteTransition(label = "aurora").animateFloat(
        initialValue = 0f, targetValue = (Math.PI * 2 * 10).toFloat(),
        animationSpec = infiniteRepeatable(tween(200_000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    Canvas(Modifier.fillMaxSize()) {
        val blobs = listOf(
            floatArrayOf(0.12f + 0.06f * sin(t * 0.4f), 0.0f, 0.75f, 0f),
            floatArrayOf(0.85f + 0.1f * cos(t * 0.33f), 0.15f, 0.6f, 0.07f),
            floatArrayOf(0.45f + 0.2f * sin(t * 0.21f + 2), 1.05f, 0.7f, -0.06f),
        )
        val side = max(size.width, size.height)
        for (b in blobs) {
            val center = Offset(size.width * b[0], size.height * b[1])
            val r = side * b[2] * 0.7f
            val c = Hue.color(hue + b[3], 0.8, 1.0, alpha = 0.38f)
            drawCircle(Brush.radialGradient(listOf(c, c.copy(alpha = 0f)), center, r), r, center)
        }
    }
}

@Composable
private fun Clock() {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000 - now % 1000)
        }
    }
    val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Good night"
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(now)),
            color = Color.White,
            fontSize = 72.sp,
            fontWeight = FontWeight.Thin,
        )
        Text(greeting, color = Color.White.copy(alpha = 0.85f), fontSize = 17.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SearchField(onSubmit: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    Row(
        Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .glass(RoundedCornerShape(26.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = Color.White.copy(alpha = 0.7f))
        Spacer(Modifier.size(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Search or enter address", color = Color.White.copy(alpha = 0.5f), fontSize = 16.sp)
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                cursorBrush = SolidColor(Hue.accentLight),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onGo = { if (query.isNotBlank()) onSubmit(query) }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Links(onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.widthIn(max = 560.dp)) {
        defaultLinks.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { link -> LinkTile(link, Modifier.weight(1f)) { onOpen(link.url) } }
            }
        }
    }
}

@Composable
private fun LinkTile(link: QuickLink, modifier: Modifier, onClick: () -> Unit) {
    val hue = Hue.site(link.host)
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .glass(RoundedCornerShape(18.dp), tint = Hue.color(hue, 0.75, 1.0, alpha = 0.28f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(link.title.take(1), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        Text(link.title, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun PrivateNote() {
    Column(
        Modifier
            .widthIn(max = 460.dp)
            .glass(RoundedCornerShape(20.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(Hue.color(Hue.PRIVATE, 0.6, 1.0, 0.35f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.VisibilityOff, null, tint = Color.White, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.size(8.dp))
            Text("Private tab", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (Browser.privateProfileSupported)
                "No history and no saved session. Cookies and site data live in their own jar and vanish when you close your last private tab."
            else
                "No history and no saved session. This device's WebView is too old to keep private cookies separate, so sign-ins may be shared with normal tabs.",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}
