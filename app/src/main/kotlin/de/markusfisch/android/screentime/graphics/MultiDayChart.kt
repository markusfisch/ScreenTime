package de.markusfisch.android.screentime.graphics

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import de.markusfisch.android.screentime.app.db
import de.markusfisch.android.screentime.app.prefs
import de.markusfisch.android.screentime.database.DAY_IN_MS
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class MultiDayChart(
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
	private val monthLinePaint =
		Paint(linePaintBold).also { it.strokeWidth = 4f; it.color = monthColor }

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
			drawAt(timestamp)
		}
	}

	private fun nextBitmap(): Bitmap {
		// Double buffering to avoid modifying the
		// currently displayed bitmap.
		even = even xor true
		return if (even) bitmapA else bitmapB
	}

	private fun Canvas.drawAt(timestamp: Long) {
		val dayStarts = LongArray(days + 1) { 0 }

		var to = prefs.dayStart(timestamp)
		for (i in 1..48) {
			to = prefs.dayStart(timestamp + i * DAY_IN_MS / 24)
			if (to > timestamp)
				break
		}

		var from = to
		for (i in 0..days) {
			dayStarts[days - i] = from
			from = prefs.dayStart(from - 1)
		}

		drawRecords(dayStarts)
		drawHours(prefs.hourOfDayChange)
		drawDays(dayStarts)
	}

	private fun Canvas.drawHours(hourOffset: Int) {
		val width = this.width - padding * 2
		val height = days * dayHeight

		val hours = 24
		for (i in 0..hours) {
			val x = (i * width / hours + padding).toFloat()
			val h = (i + hourOffset) % 24
			val paint = when (h % 6 == 0) {
				true -> linePaintBold
				false -> linePaint
			}
			if (h % 3 == 0) {
				drawTextCenteredAbove("$h", x, offsetY.toFloat())
			}
			drawLine(x, offsetY.toFloat(), x, (height + offsetY).toFloat(), paint)
		}
	}

	private fun Canvas.drawDays(dayStarts: LongArray) {
		val sX = (padding).toFloat()
		val eX = (width - padding).toFloat()

		val calendar = Calendar.getInstance()

		for (d in 0..days) {
			val y = d * dayHeight + offsetY
			calendar.timeInMillis = dayStarts[days - d]

			val paint = if (calendar.get(Calendar.DAY_OF_MONTH) == 1) {
				monthLinePaint
			} else if (calendar.get(Calendar.DAY_OF_WEEK) == calendar.firstDayOfWeek) {
				linePaintBold
			} else {
				linePaint
			}
			drawLine(sX, y.toFloat(), eX, y.toFloat(), paint)
		}
	}

	/* Q: Why do we iterate over the days?
	 * A: Because not all days have 24h hours - daytime change. */
	private fun Canvas.drawRecords(dayStarts: LongArray) {
		val dayUsage = LongArray(days) { 0 }
		val dayLastTimestamp = LongArray(days) { 0 }

		val minimumDurationLengthen = prefs.minDurationLengthenValue().toLong()

		val width = this.width - padding * 2

		val drawInOneDay = fun(day: Int, start: Long, end: Long) {
			val dayFromTop = days - 1 - day

			/* This happened with daytime change. It should be fixed, but if it happens again,
			 * the app will not crash. */
			if (dayFromTop < 0)
				return

			val start = max(start, dayLastTimestamp[dayFromTop])
			if (start <= end) {
				dayUsage[dayFromTop] += end - start
				dayLastTimestamp[dayFromTop] = end

				val end = min(end, DAY_IN_MS)

				val top = dayFromTop * dayHeight + offsetY
				val bottom = (dayFromTop + 1) * dayHeight + offsetY
				val left = (start * width / DAY_IN_MS + padding).toInt()
				val right = (end * width / DAY_IN_MS + padding).toInt()
				drawRect(Rect(left, top, right, bottom), usagePaint)
			}
		}

		var day = 0
		db.forEachRecordBetween(dayStarts.first(), dayStarts.last()) { start, duration ->
			while (start > dayStarts[day + 1])
				day++

			val end = start + max(duration, minimumDurationLengthen)

			var dayE = day
			while (end > (dayStarts.getOrNull(dayE + 1) ?: end))
				dayE++

			val s = start - dayStarts[day]
			val e = end - dayStarts[dayE]

			if (day == dayE) {
				drawInOneDay(day, s, e)
			} else {
				drawInOneDay(day, s, dayStarts[day + 1] - dayStarts[day])
				for (d in (day + 1)..<dayE) {
					drawInOneDay(dayE, 0, dayStarts[d + 1] - dayStarts[d])
				}
				drawInOneDay(dayE, 0, e)
			}
		}

		for (d in 0..<days) {
			drawCircle(
				(padding + min(dayUsage[d], DAY_IN_MS) * width / DAY_IN_MS).toFloat(),
				offsetY + dayHeight * (d + 0.5f),
				dayHeight / 6f,
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
