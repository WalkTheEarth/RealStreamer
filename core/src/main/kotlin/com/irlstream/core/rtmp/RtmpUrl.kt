package com.irlstream.core.rtmp

import java.io.IOException

class RtmpException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * A parsed RTMP ingest URL.
 *
 * `rtmp://host[:port]/app/streamKey` where the app is the first path segment and
 * everything after it is the stream name (stream keys may contain slashes and query
 * parameters, e.g. `...?bandwidthtest=true`).
 */
data class RtmpUrl(
    val secure: Boolean,
    val host: String,
    val port: Int,
    val app: String,
    val streamName: String,
) {
    val scheme: String get() = if (secure) "rtmps" else "rtmp"
    val defaultPort: Int get() = if (secure) 443 else 1935
    val effectivePort: Int get() = port

    val tcUrl: String
        get() = buildString {
            append(scheme).append("://")
            if (host.contains(':')) append('[').append(host).append(']') else append(host)
            if (port != defaultPort) append(':').append(port)
            append('/').append(app)
        }

    val publishUrl: String
        get() = buildString {
            append(scheme).append("://")
            if (host.contains(':')) append('[').append(host).append(']') else append(host)
            if (port != defaultPort) append(':').append(port)
            append('/').append(app).append('/').append(streamName)
        }

    /** Safe for logs and the UI: hides the middle of the stream key. */
    fun masked(): String {
        val visible = 4
        val maskedStream = when {
            streamName.length <= visible -> "••••"
            else -> "••••••" + streamName.takeLast(visible)
        }
        return "$scheme://$host${if (port != defaultPort) ":$port" else ""}/$app/$maskedStream"
    }

    companion object {
        fun parse(raw: String): RtmpUrl? {
            val trimmed = raw.trim()
            val schemeEnd = trimmed.indexOf("://")
            if (schemeEnd <= 0) return null
            val scheme = trimmed.substring(0, schemeEnd).lowercase()
            val secure = when (scheme) {
                "rtmp" -> false
                "rtmps" -> true
                else -> return null
            }
            val rest = trimmed.substring(schemeEnd + 3)
            if (rest.isBlank()) return null
            val slash = rest.indexOf('/')
            if (slash <= 0) return null
            var hostPort = rest.substring(0, slash)
            val path = rest.substring(slash + 1)
            if (path.isBlank()) return null

            // userinfo is not meaningful for RTMP ingest; strip it if present.
            hostPort = hostPort.substringAfterLast('@')

            val host: String
            val port: Int
            if (hostPort.startsWith("[")) {
                val close = hostPort.indexOf(']')
                if (close < 0) return null
                host = hostPort.substring(1, close)
                val remains = hostPort.substring(close + 1)
                port = when {
                    remains.isEmpty() -> if (secure) 443 else 1935
                    remains.startsWith(":") -> remains.substring(1).toIntOrNull() ?: return null
                    else -> return null
                }
            } else {
                val colon = hostPort.lastIndexOf(':')
                if (colon > 0) {
                    host = hostPort.substring(0, colon)
                    port = hostPort.substring(colon + 1).toIntOrNull() ?: return null
                } else {
                    host = hostPort
                    port = if (secure) 443 else 1935
                }
            }
            if (host.isBlank() || port !in 1..65535) return null

            val app = path.substringBefore('/')
            val streamName = path.substringAfter('/', missingDelimiterValue = "")
            if (app.isBlank() || streamName.isBlank()) return null

            return RtmpUrl(secure = secure, host = host, port = port, app = app, streamName = streamName)
        }

        fun isValid(raw: String): Boolean = parse(raw) != null
    }
}
