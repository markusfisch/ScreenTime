package de.markusfisch.android.screentime.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.MotionEvent
import de.markusfisch.android.screentime.R
import de.markusfisch.android.screentime.app.prefs
import de.markusfisch.android.screentime.graphics.loadColor
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

fun checkResetMarkerDrag(context: Context) {
	val view = LayoutInflater.from(context).inflate(R.layout.activity_main, null)
		.findViewById<UsageGraphView>(R.id.graph)
	val size = 1000
	val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
	val canvas = Canvas(bitmap)
	val radius = (size - 2 * (32f * context.resources.displayMetrics.density).roundToInt()) * .475f
	fun touch(action: Int, angle: Double) {
		val event = MotionEvent.obtain(0L, 0L, action,
			500f + cos(angle).toFloat() * radius,
			500f + sin(angle).toFloat() * radius, 0)
		try {
			check(view.onTouchEvent(event))
		} finally {
			event.recycle()
		}
	}
	view.layout(0, 0, size, size)
	touch(MotionEvent.ACTION_DOWN, prefs.hourOfDayChange * Math.PI / 12 - Math.PI / 2)
	view.onDayChangeChange = {
		// Changing the Activity title can cause another layout during a drag.
		view.layout(0, 0, size + 2, size)
		view.layout(0, 0, size, size)
	}
	try {
		for (hour in 0..23) {
			val angle = hour * Math.PI / 12 - Math.PI / 2
			touch(MotionEvent.ACTION_MOVE, angle)
			view.draw(canvas)
			val x = (500 + cos(angle) * radius).roundToInt()
			val y = (500 + sin(angle) * radius).roundToInt()
			check(bitmap.getPixel(x, y) == context.loadColor(R.color.accent)) {
				"Reset marker moved during layout at hour $hour"
			}
		}
	} finally {
		touch(MotionEvent.ACTION_CANCEL, 0.0)
		bitmap.recycle()
	}
}
