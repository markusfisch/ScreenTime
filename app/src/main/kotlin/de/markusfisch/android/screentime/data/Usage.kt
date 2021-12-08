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
	useColor: Int,
	dialColor: Int,
	numberColor: Int
): Bitmap {
	val rect = minSquare(width, height)
	val bitmap = Bitmap.createBitmap(
		rect.width().roundToInt(),
		rect.height().roundToInt(),
		Bitmap.Config.ARGB_8888
	)
	val canvas = Canvas(bitmap)
	canvas.drawClockFace(
		rect,
		fillPaint(dialColor),
		fillPaint(numberColor).apply {
			typeface = Typeface.DEFAULT_BOLD
		}
	)
	val paint = fillPaint(
		((255f / days).roundToInt() shl 24) or (useColor and 0xffffff)
	)
	canvas.drawRecordsBetween(
		startOfDay(timestamp - DAY_IN_MS * days),
		endOfDay(timestamp),
		rect,
		paint
	)
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

private const val TAU = Math.PI + Math.PI
private const val PI2 = Math.PI * .5
private fun Canvas.drawClockFace(
	rect: RectF,
	dialPaint: Paint,
	textPaint: Paint
) {
	drawCircle(
		rect.centerX(),
		rect.centerY(),
		min(rect.centerX(), rect.centerY()),
		dialPaint
	)
	val dialRadius = rect.width() / 2f
	val numberRadius = dialRadius * .85f
	val dotRadius = dialRadius * .95f
	val dotSize = dotRadius * .01f
	textPaint.textSize = dotRadius * .1f
	val centerX = rect.centerX()
	val centerY = rect.centerY()
	val steps = 24
	val step = TAU / steps
	var angle = 0.0
	var i = steps
	do {
		val a = angle - PI2
		drawNumber(
			"$i",
			centerX + numberRadius * cos(a).toFloat(),
			centerY + numberRadius * sin(a).toFloat(),
			textPaint
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

private val textBounds = Rect()
private fun Canvas.drawNumber(
	text: String,
	x: Float,
	y: Float,
	textPaint: Paint
) {
	textPaint.getTextBounds(text, 0, text.length, textBounds)
	drawText(
		text,
		x - textBounds.centerX().toFloat(),
		y - textBounds.centerY().toFloat(),
		textPaint
	)
}

private fun Canvas.drawRecordsBetween(
	from: Long,
	to: Long,
	rect: RectF,
	paint: Paint
) = db.forEachRecordBetween(from, to) { start, duration ->
	drawArc(
		rect,
		dayTimeToAngle(start) - 90f,
		dayTimeToAngle(duration),
		true,
		paint
	)
}

private fun dayTimeToAngle(ms: Long): Float = 360f / DAY_IN_MS * ms.toFloat()
