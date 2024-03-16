package de.markusfisch.android.screentime.activity

import android.app.Activity
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import android.os.Bundle
import de.markusfisch.android.screentime.R
import de.markusfisch.android.screentime.graphics.MultidayChart
import de.markusfisch.android.screentime.graphics.loadColor
import de.markusfisch.android.screentime.service.msToNextFullMinute
import de.markusfisch.android.screentime.widget.BitmapView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MultidayActivity : Activity() {
	private val job = SupervisorJob()
	private val scope = CoroutineScope(Dispatchers.Default + job)
	private val usagePaint by lazy {
		fillPaint(loadColor(R.color.usage)).apply {
			xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
		}
	}
	private val usageDotPaint by lazy {
		fillPaint(loadColor(R.color.usage_dot))
	}
	private val textPaint by lazy {
		fillPaint(loadColor(R.color.text)).apply {
			typeface = Typeface.DEFAULT_BOLD
		}
	}

	private val days = 90

	private lateinit var usageView: BitmapView

	private var updateUsageRunnable: Runnable? = null
	private var multidayChart: MultidayChart? = null
	private var paused = true

	override fun onCreate(state: Bundle?) {
		super.onCreate(state)
		setContentView(R.layout.multiday)
		usageView = findViewById(R.id.graph)
	}

	override fun onResume() {
		super.onResume()
		// Post to run update() after layout.
		postUsageUpdate(1)
		paused = false
	}

	override fun onPause() {
		super.onPause()
		paused = true
		cancelUsageUpdate()
	}

	override fun onDestroy() {
		super.onDestroy()
		job.cancelChildren()
	}

	private fun update(timestamp: Long = System.currentTimeMillis()) {
		val width = usageView.measuredWidth
		if (width < 1) {
			postUsageUpdate()
			return
		}
		val chart = getUsageChart(width) ?: return
		scope.launch {
			val bitmap = chart.draw(timestamp)
			withContext(Dispatchers.Main) {
				usageView.setBitmap(bitmap)
				if (!paused) {
					postUsageUpdate(msToNextFullMinute())
				}
			}
		}
	}

	private fun getUsageChart(width: Int): MultidayChart? {
		val dp = resources.displayMetrics.density
		if (multidayChart == null || multidayChart?.width != width) {
			multidayChart = MultidayChart(
				width,
				dp,
				days,
				usagePaint,
				textPaint,
				usageDotPaint,
				loadColor(R.color.month_separator)
			)
		}
		return multidayChart
	}

	private fun postUsageUpdate(delay: Long = 100L) {
		cancelUsageUpdate()
		updateUsageRunnable = Runnable {
			update()
		}
		usageView.postDelayed(updateUsageRunnable, delay)
	}

	private fun cancelUsageUpdate() {
		if (updateUsageRunnable != null) {
			usageView.removeCallbacks(updateUsageRunnable)
			updateUsageRunnable = null
		}
	}
}

private fun fillPaint(col: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
	color = col
	style = Paint.Style.FILL
}