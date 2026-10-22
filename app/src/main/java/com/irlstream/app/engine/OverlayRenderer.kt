package com.irlstream.app.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import com.irlstream.core.model.SourceConfig
import com.irlstream.core.model.TextAlignment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Draws broadcast overlay layers into ARGB bitmaps which the compositor uploads as
 * GL textures. All sizes are in output pixels, scaled from the 1080p reference.
 */
object OverlayRenderer {

    fun text(
        config: SourceConfig.Text,
        width: Int,
        height: Int,
        tokens: Map<String, String>,
        scale: Float,
    ): Bitmap? {
        if (width <= 0 || height <= 0) return null
        val resolved = if (config.dynamic) resolveTokens(config.text, tokens) else config.text
        if (resolved.isBlank()) return null
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bg = config.backgroundArgb.toInt()
        if (Color.alpha(bg) > 0) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg }
            val radius = 18f * scale
            canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), radius, radius, paint)
        }

        val pad = (config.paddingDp * scale * 1.5f).coerceAtLeast(6f)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = config.colorArgb.toInt()
            textSize = (config.fontSizeSp * scale).coerceAtLeast(8f)
            typeface = Typeface.create(
                if (config.bold) "sans-serif" else "sans-serif-medium",
                if (config.bold) Typeface.BOLD else Typeface.NORMAL,
            )
            setShadowLayer(3.5f * scale, 0f, 1.8f * scale, 0xB3000000.toInt())
        }
        val available = (width - 2 * pad).toInt().coerceAtLeast(1)
        val alignment = when (config.alignment) {
            TextAlignment.START -> Layout.Alignment.ALIGN_NORMAL
            TextAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
            TextAlignment.END -> Layout.Alignment.ALIGN_OPPOSITE
        }
        val layout = StaticLayout.Builder
            .obtain(resolved, 0, resolved.length, textPaint, available)
            .setAlignment(alignment)
            .setIncludePad(false)
            .setLineSpacing(0f, 1.05f)
            .setMaxLines(3)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

        canvas.save()
        val y = ((height - layout.height) / 2f).coerceAtLeast(pad)
        canvas.translate(pad, y.coerceAtMost(height - layout.height - pad))
        layout.draw(canvas)
        canvas.restore()
        return bitmap
    }

    fun chat(
        config: SourceConfig.Chat,
        messages: List<ChatMessage>,
        width: Int,
        height: Int,
        scale: Float,
    ): Bitmap? {
        if (width <= 0 || height <= 0) return null
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = 20f * scale
        val panel = config.panelArgb.toInt()
        val accent = config.accentArgb.toInt()

        if (Color.alpha(panel) > 0) {
            canvas.drawRoundRect(
                RectF(0f, 0f, width.toFloat(), height.toFloat()),
                radius, radius,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = panel },
            )
        }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * scale
            color = (accent and 0x00FFFFFF) or (0x33000000)
        }
        canvas.drawRoundRect(
            RectF(1f, 1f, width - 1f, height - 1f),
            radius, radius, border,
        )

        val pad = 18f * scale
        val base = (config.fontSizeSp * scale).coerceAtLeast(10f)
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            textSize = base * 0.62f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.14f
        }
        val headerBaseline = pad + headerPaint.textSize
        canvas.drawCircle(pad + 4f * scale, headerBaseline - headerPaint.textSize * 0.32f, 3.2f * scale, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent })
        canvas.drawText("LIVE CHAT", pad + 14f * scale, headerBaseline, headerPaint)

        val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (config.textArgb.toInt() and 0x00FFFFFF) or 0x88000000.toInt()
            textSize = base * 0.55f
            typeface = Typeface.create("monospace", Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(messages.size.toString().padStart(2, '0'), width - pad, headerBaseline, countPaint)

        val contentTop = headerBaseline + 14f * scale
        val contentBottom = height - pad
        val contentWidth = (width - 2 * pad).toInt().coerceAtLeast(1)
        val visible = messages
            .filter { !(config.hideCommandMessages && it.text.trimStart().startsWith("!")) }
            .takeLast(config.maxMessages.coerceIn(1, 20))

        data class Line(val layout: StaticLayout, val newest: Boolean)

        val items = ArrayList<Line>()
        var totalHeight = 0f
        val timestampFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        for ((index, message) in visible.withIndex()) {
            val builder = SpannableStringBuilder()
            if (config.showTimestamps) {
                builder.append(timestampFormat.format(Date(message.timestampMs)))
                builder.append("  ")
                val start = 0
                builder.setSpan(
                    ForegroundColorSpan((config.textArgb.toInt() and 0x00FFFFFF) or 0x66000000.toInt()),
                    start, builder.length, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            if (config.showNames) {
                val nameStart = builder.length
                val author = if (message.author.length > 22) message.author.take(21) + "…" else message.author
                builder.append(author)
                val color = if (Color.alpha(message.colorArgb.toInt()) == 0) accent else message.colorArgb.toInt()
                builder.setSpan(
                    ForegroundColorSpan(color),
                    nameStart, builder.length, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                builder.setSpan(
                    StyleSpan(Typeface.BOLD),
                    nameStart, builder.length, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                builder.append("  ")
            }
            val textStart = builder.length
            builder.append(message.text.replace("\n", " "))
            builder.setSpan(
                ForegroundColorSpan(config.textArgb.toInt()),
                textStart, builder.length, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE,
            )

            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = base
                color = config.textArgb.toInt()
                this.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                setShadowLayer(2.5f * scale, 0f, 1f * scale, 0x99000000.toInt())
            }
            val layout = StaticLayout.Builder
                .obtain(builder, 0, builder.length, paint, contentWidth)
                .setIncludePad(false)
                .setLineSpacing(0f, 1.05f)
                .setMaxLines(3)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
            val spacing = 10f * scale
            items += Line(layout, newest = index == visible.lastIndex)
            totalHeight += layout.height + spacing
        }

        // Drop oldest lines until they fit above the bottom edge.
        var startIndex = 0
        while (startIndex < items.size && totalHeight > (contentBottom - contentTop)) {
            totalHeight -= items[startIndex].layout.height + 10f * scale
            startIndex++
        }

        var y = contentBottom - totalHeight + 10f * scale
        for (i in startIndex until items.size) {
            val item = items[i]
            if (item.newest) {
                val highlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = (accent and 0x00FFFFFF) or 0x22000000.toInt()
                }
                canvas.drawRoundRect(
                    RectF(pad - 8f * scale, y - 6f * scale, width - pad + 8f * scale, y + item.layout.height + 4f * scale),
                    8f * scale, 8f * scale, highlight,
                )
            }
            canvas.save()
            canvas.translate(pad, y)
            item.layout.draw(canvas)
            canvas.restore()
            y += item.layout.height + 10f * scale
        }
        return bitmap
    }

    fun stats(
        config: SourceConfig.Stats,
        stats: StatsSnapshot,
        width: Int,
        height: Int,
        scale: Float,
    ): Bitmap? {
        if (width <= 0 || height <= 0) return null
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = 16f * scale
        val panel = config.panelArgb.toInt()
        val accent = config.accentArgb.toInt()
        val fg = config.colorArgb.toInt()

        if (Color.alpha(panel) > 0) {
            canvas.drawRoundRect(
                RectF(0f, 0f, width.toFloat(), height.toFloat()),
                radius, radius,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = panel },
            )
        }
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        canvas.drawRoundRect(
            RectF(0f, 10f * scale, 3.5f * scale, height - 10f * scale),
            2f * scale, 2f * scale, barPaint,
        )

        val pad = 18f * scale
        val base = (config.fontSizeSp * scale).coerceAtLeast(9f)
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (fg and 0x00FFFFFF) or 0x99000000.toInt().toInt()
            textSize = base * 0.62f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.12f
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fg
            textSize = base
            typeface = Typeface.create("monospace", Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }

        val rows = ArrayList<Pair<String, String>>()
        if (config.showFps) rows += "FPS" to String.format(Locale.US, "%.1f", stats.fps)
        if (config.showBitrate) rows += "BITRATE" to String.format(Locale.US, "%.1f Mbps", stats.bitrateKbps / 1000f)
        if (config.showDropped) rows += "DROPPED" to stats.droppedFrames.toString()
        if (config.showUptime) rows += "UPTIME" to formatDuration(stats.uptimeMs)
        if (config.showDestinations) rows += "OUTPUTS" to "${stats.destinationsLive}/${stats.destinationsTotal}"

        val headerHeight = base * 1.35f
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            textSize = base * 0.6f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.14f
        }
        canvas.drawCircle(pad + 3f * scale, pad + headerPaint.textSize * 0.7f, 3f * scale, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent })
        canvas.drawText("LIVE STATS", pad + 13f * scale, pad + headerPaint.textSize, headerPaint)

        val rowHeight = (height - pad * 2 - headerHeight) / rows.size.coerceAtLeast(1)
        var y = pad + headerHeight + rowHeight * 0.68f
        for ((label, value) in rows) {
            canvas.drawText(label, pad, y, labelPaint)
            canvas.drawText(value, width - pad, y, valuePaint)
            y += rowHeight
            if (y < height - pad) {
                canvas.drawLine(
                    pad, y - rowHeight * 0.42f, width - pad, y - rowHeight * 0.42f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x14FFFFFF },
                )
            }
        }
        return bitmap
    }

    fun placeholder(width: Int, height: Int, label: String): Bitmap? {
        if (width <= 0 || height <= 0) return null
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCCE9FFF0.toInt()
            textSize = (height * 0.12f).coerceAtLeast(12f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(label, width / 2f, height / 2f - (paint.descent() + paint.ascent()) / 2f, paint)
        return bitmap
    }

    private fun resolveTokens(text: String, tokens: Map<String, String>): String {
        var out = text
        tokens.forEach { (key, value) -> out = out.replace("{$key}", value) }
        return out
    }

    fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }
}
