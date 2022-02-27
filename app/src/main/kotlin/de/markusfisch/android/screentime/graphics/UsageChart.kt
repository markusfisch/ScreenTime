package de.markusfisch.android.screentime.graphics

import android.graphics.*
import de.markusfisch.android.screentime.app.db
import de.markusfisch.android.screentime.app.prefs
import de.markusfisch.android.screentime.data.DAY_IN_MS
import de.markusfisch.android.screentime.data.startOfDay
import de.markusfisch.android.screentime.data.timeRangeColloquial
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

const val TAU = Math.PI + Math.PI
const val PI2 = Math.PI / 2

class UsageChart(
	val width: Int,
	val height: Int,
	private val usagePaint: Paint,
	private val dialPaint: Paint,
	private val textPaint: Paint
) {
	private val bitmapA = Bitmap.createBitmap(
		width,
		height,
		Bitmap.Config.ARGB_8888
	)
	private val bitmapB = bitmapA.copy(bitmapA.config, true)
	private val squareRect = RectF()
	private val numberBounds = Rect()
	private val sumBounds = Rect()
	private val daysBounds = Rect()
	private val sumPaint = Paint(Paint.ANTI_ALIAS_FLAG)

	private var even = true

	fun draw(
		timestamp: Long,
		days: Int,
		lastDaysString: String
	): Bitmap = nextBitmap().apply {
		Canvas(this).apply {
			drawColor(0, PorterDuff.Mode.CLEAR)
			drawAt(
				width / 2f,
				height / 2f,
				min(width, height) / 2f,
				timestamp,
				days,
				lastDaysString,
			)
		}
	}

	private fun nextBitmap(): Bitmap {
		// Double buffering to avoid modifying the
		// currently displayed bitmap.
		even = even xor true
		return if (even) bitmapA else bitmapB
	}

	private fun Canvas.drawAt(
		centerX: Float,
		centerY: Float,
		radius: Float,
		timestamp: Long,
		days: Int,
		lastDaysString: String
	) {
		drawCircle(centerX, centerY, radius, dialPaint)
		val dayStart = prefs.dayStart(timestamp - DAY_IN_MS * days)
		val dayEnd = prefs.dayStart(timestamp) + DAY_IN_MS
		val seconds = drawRecordsBetween(
			dayStart,
			dayEnd,
			squareRect.apply {
				set(
					centerX - radius,
					centerY - radius,
					centerX + radius,
					centerY + radius
				)
			},
			usagePaint
		)
		drawClockFace(centerX, centerY, radius, textPaint)
		drawCenter(
			centerX,
			centerY,
			radius * .45f,
			dialPaint,
			textPaint,
			timeRangeColloquial(seconds),
			lastDaysString
		)
	}

	private fun Canvas.drawClockFace(
		centerX: Float,
		centerY: Float,
		radius: Float,
		textPaint: Paint
	) {
		val numberRadius = radius * .85f
		val dotRadius = radius * .95f
		val dotSize = dotRadius * .01f
		textPaint.textSize = dotRadius * .1f
		val steps = 24
		val step = TAU / steps
		var angle = 0.0
		var i = steps
		do {
			val a = angle - PI2
			drawTextCentered(
				"$i",
				centerX + numberRadius * cos(a).toFloat(),
				centerY + numberRadius * sin(a).toFloat(),
				textPaint,
				numberBounds
			)
			i = (i + 1) % steps
			angle += step
		} while (i > 0)
		i = steps * 4
		val smallDotSize = dotSize * .5f
		val smallStep = step / 4f
		while (i > -1) {
			val a = angle - PI2
			drawCircle(
				centerX + dotRadius * cos(a).toFloat(),
				centerY + dotRadius * sin(a).toFloat(),
				if (i % 4 == 0) dotSize else smallDotSize,
				textPaint
			)
			angle += smallStep
			--i
		}
	}

	private fun Canvas.drawCenter(
		centerX: Float,
		centerY: Float,
		radius: Float,
		dialPaint: Paint,
		textPaint: Paint,
		sumText: String,
		daysText: String
	) {
		drawCircle(centerX, centerY, radius, dialPaint)
		sumPaint.apply {
			color = textPaint.color
			style = textPaint.style
			typeface = textPaint.typeface
			textSize = radius * .3f
			getTextBounds(sumText, 0, sumText.length, sumBounds)
		}
		textPaint.apply {
			textSize = radius * .2f
			getTextBounds(daysText, 0, daysText.length, daysBounds)
		}
		val half = (sumBounds.height() + daysBounds.height() * 1.75f) / 2f
		val top = centerY - half
		val bottom = centerY + half
		drawText(
			sumText,
			centerX - sumBounds.centerX(),
			top + sumBounds.height() / 2 - sumBounds.centerY(),
			sumPaint
		)
		drawText(
			daysText,
			centerX - daysBounds.centerX(),
			bottom - daysBounds.height() / 2 - daysBounds.centerY(),
			textPaint
		)
	}
}

private fun Canvas.drawRecordsBetween(
	from: Long,
	to: Long,
	rect: RectF,
	paint: Paint
): Long {
	val dayStart = startOfDay(from)
	var total = 0L
	db.forEachRecordBetween(from, to) { start, duration ->
		total += duration
		drawArc(
			rect,
			dayTimeToAngle(start - dayStart) - 90f,
			dayTimeToAngle(duration),
			true,
			paint
		)
	}
	return total / 1000L
}

private fun dayTimeToAngle(ms: Long): Float = 360f / DAY_IN_MS * ms.toFloat()

private fun Canvas.drawTextCentered(
	text: String,
	x: Float,
	y: Float,
	textPaint: Paint,
	textBounds: Rect
) {
	textPaint.getTextBounds(text, 0, text.length, textBounds)
	drawText(
		text,
		x - textBounds.centerX().toFloat(),
		y - textBounds.centerY().toFloat(),
		textPaint
	)
}
