package de.markusfisch.android.screentime.data

import android.graphics.*
import de.markusfisch.android.screentime.app.db
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val DAY_IN_MS = 86400000L

fun drawUsageChart(
	width: Int,
	height: Int,
	timestamp: Long,
	days: Int,
	lastDaysString: String,
	usageColor: Int,
	dialColor: Int,
	numberColor: Int
): Bitmap {
	val rect = minSquare(width, height)
	val bitmap = Bitmap.createBitmap(
		rect.width().roundToInt(),
		rect.height().roundToInt(),
		Bitmap.Config.ARGB_8888
	)
	Canvas(bitmap).apply {
		val dialPaint = fillPaint(dialColor)
		drawCircle(
			rect.centerX(),
			rect.centerY(),
			min(rect.centerX(), rect.centerY()),
			dialPaint
		)
		val total = drawRecordsBetween(
			startOfDay(timestamp - DAY_IN_MS * days),
			endOfDay(timestamp),
			rect,
			fillPaint(
				((255f / (days + 1)).roundToInt() shl 24) or
						(usageColor and 0xffffff)
			).apply {
				xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
			}
		)
		val textPaint = fillPaint(numberColor).apply {
			typeface = Typeface.DEFAULT_BOLD
		}
		drawClockFace(rect, textPaint)
		drawCenter(
			rect,
			dialPaint,
			textPaint,
			String.format(
				"%02d:%02d",
				total / 3600,
				(total / 60) % 60
			),
			lastDaysString
		)
	}
	return bitmap
}

private fun fillPaint(col: Int) = paint(col).apply {
	style = Paint.Style.FILL
}

private fun paint(col: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
	color = col
}

private fun minSquare(width: Int, height: Int): RectF {
	val size = min(width, height).toFloat()
	return RectF(0f, 0f, size, size)
}

private fun Canvas.drawRecordsBetween(
	from: Long,
	to: Long,
	rect: RectF,
	paint: Paint
): Long {
	var total = 0L
	fun drawPie(start: Long, duration: Long) {
		total += duration
		drawArc(
			rect,
			dayTimeToAngle(start) - 90f,
			dayTimeToAngle(duration),
			true,
			paint
		)
	}

	val lastStart = db.forEachRecordBetween(from, to) { start, duration ->
		drawPie(start, duration)
	}
	// Draw the ongoing record, if there's one.
	if (lastStart > 0L) {
		drawPie(lastStart, System.currentTimeMillis() - lastStart)
	}
	return total / 1000L
}

private fun dayTimeToAngle(ms: Long): Float = 360f / DAY_IN_MS * ms.toFloat()

private const val TAU = Math.PI + Math.PI
private const val PI2 = Math.PI / 2
private fun Canvas.drawClockFace(
	rect: RectF,
	textPaint: Paint
) {
	val dialRadius = rect.width() / 2f
	val numberRadius = dialRadius * .85f
	val dotRadius = dialRadius * .95f
	val dotSize = dotRadius * .01f
	textPaint.textSize = dotRadius * .1f
	val centerX = rect.centerX()
	val centerY = rect.centerY()
	val textBounds = Rect()
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
			textBounds
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

private fun Canvas.drawCenter(
	rect: RectF,
	dialPaint: Paint,
	textPaint: Paint,
	sumText: String,
	daysText: String
) {
	val cx = rect.centerX()
	val cy = rect.centerY()
	drawCircle(cx, cy, min(cx, cy) * .5f, dialPaint)
	val sumBounds = Rect()
	val sumPaint = Paint(textPaint.apply {
		textSize = cx * .2f
		getTextBounds(sumText, 0, sumText.length, sumBounds)
	})
	val daysBounds = Rect()
	textPaint.apply {
		textSize = cx * .1f
		getTextBounds(daysText, 0, daysText.length, daysBounds)
	}
	val half = (sumBounds.height() + daysBounds.height() * 1.75f) / 2f
	val top = cy - half
	val bottom = cy + half
	drawText(
		sumText,
		cx - sumBounds.centerX(),
		top + sumBounds.height() / 2 - sumBounds.centerY(),
		sumPaint
	)
	drawText(
		daysText,
		cx - daysBounds.centerX(),
		bottom - daysBounds.height() / 2 - daysBounds.centerY(),
		textPaint
	)
}