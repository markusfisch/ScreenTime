package de.markusfisch.android.screentime.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import de.markusfisch.android.screentime.app.db
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin


private const val DAY_IN_MS = 86400000L

fun drawUsageChart(
	width: Int,
	height: Int,
	days: Int,
	useColor: Int,
	dialColor: Int,
	faceColor: Int,
	faceStrokeWidth: Float
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
		fill(dialColor),
		paint(faceColor).apply {
			style = Paint.Style.STROKE
			strokeWidth = faceStrokeWidth
		}
	)
	val paint = fill(
		((255f / days).roundToInt() shl 24) or (useColor and 0xffffff)
	)
	var day = System.currentTimeMillis() - DAY_IN_MS * days
	for (i in 0 until days) {
		canvas.drawRecordsOfDay(day, rect, paint)
		day += DAY_IN_MS
	}
	return bitmap
}

private fun fill(col: Int) = paint(col).apply {
	style = Paint.Style.FILL
}

private fun paint(col: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
	color = col
}

private fun minSquare(width: Int, height: Int): RectF {
	val size = min(width, height).toFloat()
	return RectF(0f, 0f, size, size)
}

private fun Canvas.drawClockFace(rect: RectF, dialPaint: Paint, facePaint: Paint) {
	drawArc(
		rect,
		0f,
		360f,
		true,
		dialPaint
	)
	val outerRadius = rect.width() / 2f
	val innerRadius = outerRadius * .9f
	val centerX = rect.centerX()
	val centerY = rect.centerY()
	val step = 6.28 / 24.0
	var angle = 0.0
	while (angle < 6.28) {
		val cos = cos(angle).toFloat()
		val sin = sin(angle).toFloat()
		drawLine(
			centerX + innerRadius * cos,
			centerY + innerRadius * sin,
			centerX + outerRadius * cos,
			centerY + outerRadius * sin,
			facePaint
		)
		angle += step
	}
}

private fun Canvas.drawRecordsOfDay(
	timestamp: Long,
	rect: RectF,
	paint: Paint
) = db.forEachRecordOfDay(timestamp) { start, duration ->
	drawArc(
		rect,
		dayTimeToAngle(start) - 90f,
		dayTimeToAngle(duration),
		true,
		paint
	)
}

private fun dayTimeToAngle(ms: Long): Float = 360f / DAY_IN_MS * ms.toFloat()
