package org.musicnfc.writer

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

/** Link parameter parsing and deep-link construction for supported music apps. */
object MusicLinks {
    enum class Platform(val label: String) {
        QQ("QQ 音乐"),
        NETEASE("网易云音乐"),
        KUGOU("酷狗音乐"),
        QISHUI("汽水音乐")
    }

    @JvmStatic
    fun normalizeInput(input: String?): String {
        if (input == null) return ""
        val unescaped = input.replace("\\:", ":").replace("\\_", "_").trim()
        val folded = unescaped.lowercase(Locale.ROOT)
        val https = folded.indexOf("https://")
        val http = folded.indexOf("http://")
        val start = when {
            https < 0 -> http
            http < 0 -> https
            else -> minOf(https, http)
        }
        if (start < 0) return unescaped

        // Scan by code point instead of Unicode-property regexes. Some Android
        // releases reject Java's IsHan script syntax at runtime.
        var end = start
        while (end < unescaped.length) {
            val codePoint = unescaped.codePointAt(end)
            val script = Character.UnicodeScript.of(codePoint)
            val isCjk = script == Character.UnicodeScript.HAN ||
                script == Character.UnicodeScript.HIRAGANA ||
                script == Character.UnicodeScript.KATAKANA ||
                script == Character.UnicodeScript.HANGUL
            val isDelimiter = Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint) || when (codePoint) {
                '<'.code, '>'.code, '"'.code, '\''.code, '('.code, ')'.code,
                '（'.code, '）'.code, '《'.code, '》'.code, '【'.code, '】'.code -> true
                else -> false
            }
            if (isCjk || isDelimiter) break
            end += Character.charCount(codePoint)
        }
        return unescaped.substring(start, end).trimEnd { it in "。，,；;）)》>" }
    }

    @JvmStatic
    fun extractParameter(platform: Platform, link: String): String? {
        val value = normalizeInput(link)
        val id = when (platform) {
            Platform.QQ -> first(value, "songmid", "mid")
                ?: findInJsonQuery(value, "songmid")
                ?: pathAfter(value, "songDetail")
            Platform.NETEASE -> first(value, "id", "songId", "song_id")
                ?: pathAfter(value, "song")
            Platform.KUGOU -> first(value, "chain")
            Platform.QISHUI -> first(value, "track_id", "trackId")
                ?: pathAfter(value, "track")
        }
        val pattern = when (platform) {
            Platform.QQ -> "[A-Za-z0-9]+"
            Platform.NETEASE, Platform.QISHUI -> "[0-9]+"
            Platform.KUGOU -> "[A-Za-z0-9_-]+"
        }
        return id?.takeIf { it.matches(Regex(pattern)) }
    }

    @JvmStatic
    fun buildUri(platform: Platform, parameter: String): String {
        require(parameter.isNotEmpty()) { "缺少歌曲参数" }
        return when (platform) {
            Platform.QQ -> {
                val json = "{\"song\":[{\"type\":\"0\",\"songmid\":\"${jsonEscape(parameter)}\"}],\"action\":\"play\"}"
                "qqmusic://qq.com/media/playSonglist?p=${percentEncode(json)}"
            }
            Platform.NETEASE -> "orpheus://song/$parameter/?autoplay=true"
            Platform.KUGOU -> {
                val target = "m.kugou.com/share/song.html?chain="
                val json = "{\"cmd\":303,\"jsonStr\":{\"title\":\"\",\"url\":\"${target}${jsonEscape(parameter)}\"}}"
                "kugou://start.weixin?${percentEncode(json)}"
            }
            Platform.QISHUI -> "luna://luna.com/playing?track_id=${percentEncode(parameter)}"
        }
    }

    /** Follows HTTP redirects only when the pasted URL does not contain the needed ID. */
    @JvmStatic
    @Throws(Exception::class)
    fun resolveRedirect(link: String): String {
        var current = URL(normalizeInput(link))
        repeat(8) {
            if (current.protocol.lowercase(Locale.ROOT) !in setOf("http", "https")) return current.toString()
            val connection = current.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 7_000
            connection.readTimeout = 7_000
            connection.requestMethod = "GET"
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36"
            )
            connection.setRequestProperty("Range", "bytes=0-0")
            val status = connection.responseCode
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            if (location.isNullOrEmpty() || status !in 300..399) return current.toString()
            current = URL(current, location)
        }
        return current.toString()
    }

    private fun first(url: String, vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> queryParameter(url, key)?.takeIf(String::isNotEmpty) }

    private fun queryParameter(value: String, wanted: String): String? {
        val question = value.indexOf('?')
        if (question < 0) return null
        val fragment = value.indexOf('#', question + 1)
        val query = value.substring(question + 1, if (fragment < 0) value.length else fragment)
        for (pair in query.split('&')) {
            val equals = pair.indexOf('=')
            val key = decode(if (equals < 0) pair else pair.substring(0, equals))
            if (wanted == key) return decode(if (equals < 0) "" else pair.substring(equals + 1))
        }
        return null
    }

    private fun findInJsonQuery(url: String, key: String): String? {
        val json = queryParameter(url, "p") ?: return null
        return Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"([^\"]+)\"")
            .find(json)?.groupValues?.getOrNull(1)
    }

    private fun pathAfter(value: String, marker: String): String? = try {
        val path = URL(value).path
        val segments = path.split('/')
        val index = segments.indexOf(marker)
        segments.getOrNull(index + 1)?.takeIf { index >= 0 && it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }

    private fun decode(value: String): String = try {
        URLDecoder.decode(value, "UTF-8")
    } catch (_: Exception) {
        value
    }

    private fun percentEncode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun jsonEscape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")
}
