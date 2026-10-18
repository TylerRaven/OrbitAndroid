package com.mangostudios.orbit

import androidx.compose.ui.graphics.Color

/** Page colors, ported from the Mac app's Ambient.swift and Wallpaper.swift. */
object Hue {
    /** Private tabs always glow violet so you can tell at a glance. */
    const val PRIVATE = 0.76

    /** Orbit's default accent (Accent.blue on the Mac). */
    const val ACCENT = 0.61
    val accent = Color(0.25f, 0.47f, 1.0f)
    val accentLight = Color(0.36f, 0.72f, 1.0f)

    private val siteHues: List<Pair<List<String>, Double>> = listOf(
        listOf("youtube", "netflix") to 0.0,
        listOf("reddit", "amazon", "soundcloud") to 0.06,
        listOf("spotify", "whatsapp", "duolingo") to 0.38,
        listOf("twitter", "x.com", "facebook", "linkedin", "bsky", "paypal") to 0.58,
        listOf("google") to 0.6,
        listOf("discord", "twitch") to 0.7,
        listOf("github", "notion", "apple") to 0.76,
        listOf("instagram", "tiktok", "pinterest", "dribbble") to 0.93,
    )

    /** A known site color, or one derived from the domain. Matches WallpaperMood.siteHue. */
    fun site(host: String): Double {
        val h = host.lowercase()
        return siteHues.firstOrNull { (words, _) -> words.any { h.contains(it) } }?.second ?: hash(host)
    }

    /** djb2-xor over the UTF-8 bytes with 64-bit wraparound, same as the Swift version. */
    fun hash(s: String): Double {
        var h = 5381uL
        for (b in s.toByteArray(Charsets.UTF_8)) h = (h * 33uL) xor b.toUByte().toULong()
        return (h % 360uL).toDouble() / 360
    }

    /** Parses a CSS theme-color; returns null for greys so they fall back to the site color. */
    fun fromCss(css: String): Double? {
        val s = css.trim().lowercase()
        var rgb: Triple<Double, Double, Double>? = null
        if (s.startsWith("#")) {
            var hex = s.drop(1)
            if (hex.length == 3) hex = hex.map { "$it$it" }.joinToString("")
            if (hex.length >= 6) hex.take(6).toLongOrNull(16)?.let { v ->
                rgb = Triple(((v shr 16) and 0xff) / 255.0, ((v shr 8) and 0xff) / 255.0, (v and 0xff) / 255.0)
            }
        } else if (s.startsWith("rgb")) {
            val nums = Regex("[0-9.]+").findAll(s).mapNotNull { it.value.toDoubleOrNull() }.take(3).toList()
            if (nums.size == 3) rgb = Triple(nums[0] / 255, nums[1] / 255, nums[2] / 255)
        }
        val (r, g, b) = rgb ?: return null
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val saturation = if (max == 0.0) 0.0 else (max - min) / max
        if (saturation <= 0.18 || max <= 0.15) return null
        val d = max - min
        val hue = when (max) {
            r -> ((g - b) / d).mod(6.0)
            g -> (b - r) / d + 2
            else -> (r - g) / d + 4
        } / 6
        return hue
    }

    /** HSB → Color, wrapping the hue like the Swift hsb() helper. */
    fun color(hue: Double, saturation: Double = 0.7, brightness: Double = 1.0, alpha: Float = 1f): Color {
        val h = (hue.mod(1.0) * 360).toFloat()
        return Color.hsv(h, saturation.coerceIn(0.0, 1.0).toFloat(), brightness.coerceIn(0.0, 1.0).toFloat(), alpha)
    }
}
