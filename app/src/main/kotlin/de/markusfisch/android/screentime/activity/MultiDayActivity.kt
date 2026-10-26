package de.markusfisch.android.screentime.activity

import android.app.Activity
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import de.markusfisch.android.screentime.R
import de.markusfisch.android.screentime.graphics.MultiDayChart
import de.markusfisch.android.screentime.graphics.fillPaint
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

class MultiDayActivity : Activity() {
	private val job = SupervisorJob()
	private val scope = CoroutineScope(Dispatchers.Default + job)
	private val usagePaint by lazy {
		fillPaint(loadColor(R.color.usage)).apply {
			xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
		}
	}
	private val dialPaint by lazy {
		fillPaint(loadColor(R.color.dial))
	}
	private val usageDotPaint by lazy {
		fillPaint(loadColor(R.color.text))
	}
	private val textPaint by lazy {
		fillPaint(loadColor(R.color.text)).apply {
			typeface = Typeface.DEFAULT_BOLD
		}
	}

	private val days = 90

	private lateinit var usageView: BitmapView

	private var updateUsageRunnable: Runnable? = null
	private var multiDayChart: MultiDayChart? = null
	private var paused = true

	override fun onCreate(state: Bundle?) {
		super.onCreate(state)
		setContentView(R.layout.activity_multi_day)
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

	private var first = true
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
				if (first) {
					val scroll: View = findViewById(R.id.scroll)
					val prefix: View = findViewById(R.id.prefix)
					scroll.scrollTo(0, prefix.height)
					first = false
				}
				if (!paused) {
					postUsageUpdate(msToNextFullMinute())
				}
			}
		}
	}

	private fun getUsageChart(width: Int): MultiDayChart? {
		val dp = resources.displayMetrics.density
		if (multiDayChart == null || multiDayChart?.width != width) {
			multiDayChart = MultiDayChart(
				width,
				dp,
				days,
				usagePaint,
				dialPaint,
				textPaint,
				usageDotPaint
			)
		}
		return multiDayChart
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
