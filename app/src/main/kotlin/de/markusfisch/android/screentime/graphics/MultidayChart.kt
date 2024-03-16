package de.markusfisch.android.screentime.graphics

import java.util.Calendar
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import de.markusfisch.android.screentime.app.db
import de.markusfisch.android.screentime.app.prefs
import de.markusfisch.android.screentime.database.DAY_IN_MS
import kotlin.math.max
import kotlin.math.roundToInt

class MultidayChart(
	val width: Int,
	private val dp: Float,
	private val days: Int,
	private val usagePaint: Paint,
	private val textPaint: Paint,
	private val usageDotPaint: Paint,
	private val monthColor: Int
) {
	private val linePaint = Paint(textPaint).also { it.strokeWidth = 1f }
	private val linePaintBold = Paint(linePaint).also { it.strokeWidth = 2f }
	private val monthLinePaint = Paint(linePaintBold).also { it.strokeWidth = 4f; it.color = monthColor }

	private val textBounds = Rect()

	private val dayHeight = (24 * dp).roundToInt()
	private val padding = (12 * dp).roundToInt()
	private val offsetY = (32 * dp).roundToInt()

	private val bitmapA = Bitmap.createBitmap(
		width,
		dayHeight * days + offsetY + padding,
		Bitmap.Config.ARGB_8888
	)
	private val bitmapB = bitmapA.copy(bitmapA.config, true)

	private var even = true

	fun draw(timestamp: Long): Bitmap = nextBitmap().apply {
		Canvas(this).apply {
			drawColor(0, PorterDuff.Mode.CLEAR)
			drawAt( timestamp )
		}
	}

	private fun nextBitmap(): Bitmap {
		// Double buffering to avoid modifying the
		// currently displayed bitmap.
		even = even xor true
		return if (even) bitmapA else bitmapB
	}

	private fun Canvas.drawAt(timestamp: Long) {
		val dayStart = prefs.dayStart(timestamp - DAY_IN_MS * (days - 1))
		val dayEnd = prefs.dayStart(timestamp) + DAY_IN_MS

		drawRecordsBetween(dayStart, dayEnd)

		drawHours(prefs.hourOfDayChange)
		drawDays(timestamp)
	}

	private fun Canvas.drawHours(hourOffset: Int) {
		val width = this.width - padding * 2
		val height = days * dayHeight

		val hours = 24
		for (i in 0..hours) {
			val x = (i * width / hours + padding).toFloat()
			val h = (i + hourOffset) % 24
			val paint = when(h % 6 == 0) {
				true -> linePaintBold
				false -> linePaint
			}
			if (h % 3 == 0) {
				drawTextCenteredAbove("$h", x, offsetY.toFloat())
			}
			drawLine(x, offsetY.toFloat(), x, (height + offsetY).toFloat(), paint)
		}
	}
	private fun Canvas.drawDays(timestamp: Long) {
		val sX = (padding).toFloat()
		val eX = (width - padding).toFloat()

		var midday = prefs.dayStart(timestamp) + DAY_IN_MS / 2 + DAY_IN_MS

		val calendar = Calendar.getInstance()

		for (d in 0..days) {
			val y = d * dayHeight + offsetY
			calendar.timeInMillis = midday

			val paint = if (calendar.get(Calendar.DAY_OF_MONTH) == 1) {
				monthLinePaint
			} else if (calendar.get(Calendar.DAY_OF_WEEK) == calendar.firstDayOfWeek) {
				linePaintBold
			} else {
				linePaint
			}
			drawLine(sX, y.toFloat(), eX, y.toFloat(), paint)
			midday -= DAY_IN_MS
		}
	}

	private fun Canvas.drawRecordsBetween(
		from: Long, // day start
		to: Long, // day start
	) {
		/* Every record is visualized/counted for minimum 1 minute duration.
		   Not counting overlaps. */
		val minimumDuration = 1L * 60L * 1000L

		val width = this.width - padding * 2
		val days = (to - from) / DAY_IN_MS

		val dayUsage = LongArray(days.toInt()) {0}
		val dayLastTimestamp = LongArray(days.toInt()) {0}

		val drawInOneDay = { day: Long, start: Long, end: Long ->
			val dayFromTop = (days - 1 - day).toInt()
			val start = max(start, dayLastTimestamp[dayFromTop])
			if (start <= end) {
				dayUsage[dayFromTop] += end - start
				dayLastTimestamp[dayFromTop] = end

				val top = dayFromTop * dayHeight + offsetY
				val bottom = (dayFromTop + 1) * dayHeight + offsetY
				val left = (start * width / DAY_IN_MS + padding).toInt()
				val right = (end * width / DAY_IN_MS + padding).toInt()
				drawRect(Rect(left, top, right, bottom), usagePaint)
			}
		}
		db.forEachRecordBetween(from, to) { start, duration ->
			val end = start + max(duration, minimumDuration)
			val dayS = (start - from) / DAY_IN_MS
			val dayE = (end - from) / DAY_IN_MS

			val s = start - from - dayS * DAY_IN_MS
			val e = end - from - dayE * DAY_IN_MS

			if (dayS == dayE) {
				drawInOneDay(dayS, s, e)
			} else {
				drawInOneDay(dayS, s, DAY_IN_MS)
				drawInOneDay(dayE, 0, e)
			}
		}

		for (d in 0..<days.toInt()) {
			drawCircle(
				(padding + dayUsage[d] * width / DAY_IN_MS).toFloat(),
				offsetY + dayHeight * (d + 0.5f),
				dayHeight/6f,
				usageDotPaint
			)
		}
	}

	private fun Canvas.drawTextCenteredAbove(
		text: String,
		x: Float,
		y: Float
	) {
		textPaint.textSize = 12f * dp
		textPaint.getTextBounds(text, 0, text.length, textBounds)
		drawText(
			text,
			x - textBounds.centerX().toFloat(),
			y - textBounds.height().toFloat(),
			textPaint
		)
	}
}
