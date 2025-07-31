package de.markusfisch.android.screentime.activity

import android.app.Activity
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import de.markusfisch.android.screentime.R
import de.markusfisch.android.screentime.app.prefs
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
	private lateinit var minDurationLengthenBar: SeekBar

	private var updateUsageRunnable: Runnable? = null
	private var multiDayChart: MultiDayChart? = null
	private var paused = true

	override fun onCreate(state: Bundle?) {
		super.onCreate(state)
		setContentView(R.layout.activity_multi_day)
		usageView = findViewById(R.id.graph)
		minDurationLengthenBar = findViewById(R.id.minDurationLengthenBar)
		minDurationLengthenBar.initMinDurationLengthenBar()
	}

	private fun SeekBar.initMinDurationLengthenBar() {
		setOnSeekBarChangeListener(object :
			SeekBar.OnSeekBarChangeListener {
			override fun onProgressChanged(
				seekBar: SeekBar,
				progress: Int,
				fromUser: Boolean
			) {
				if (fromUser) {
					prefs.minDurationLengthen = progress
					val min = prefs.minDurationLengthenValue()
					title = getString(
						R.string.lengthen_duration,
						min / 3600000,
						min / 60000 % 60,
						min / 1000 % 60
					)
					postUsageUpdate()
				}
			}

			override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

			override fun onStopTrackingTouch(seekBar: SeekBar) {
				setTitle(R.string.detailed_list)
			}
		})
		progress = prefs.minDurationLengthen
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
		prefs.minDurationLengthen = minDurationLengthenBar.progress
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
				textPaint,
				usageDotPaint,
				loadColor(R.color.month_separator)
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
