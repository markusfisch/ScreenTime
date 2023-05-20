package de.markusfisch.android.screentime.activity

import android.app.Activity
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.SeekBar
import de.markusfisch.android.screentime.R
import de.markusfisch.android.screentime.app.db
import de.markusfisch.android.screentime.app.prefs
import de.markusfisch.android.screentime.app.requestNotificationPermission
import de.markusfisch.android.screentime.graphics.UsageChart
import de.markusfisch.android.screentime.graphics.loadColor
import de.markusfisch.android.screentime.os.isIgnoringBatteryOptimizations
import de.markusfisch.android.screentime.os.requestDisableBatteryOptimization
import de.markusfisch.android.screentime.service.msToNextFullMinute
import de.markusfisch.android.screentime.widget.UsageGraphView
import kotlinx.coroutines.*
import kotlin.math.min
import kotlin.math.roundToInt

class MainActivity : Activity() {
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
	private val textPaint by lazy {
		fillPaint(loadColor(R.color.text)).apply {
			typeface = Typeface.DEFAULT_BOLD
		}
	}

	private lateinit var usageView: UsageGraphView
	private lateinit var dayBar: SeekBar

	private var updateUsageRunnable: Runnable? = null
	private var usageChart: UsageChart? = null
	private var paused = true

	override fun onCreate(state: Bundle?) {
		super.onCreate(state)
		setContentView(R.layout.activity_main)
		usageView = findViewById(R.id.graph)
		usageView.initUsageView()
		dayBar = findViewById(R.id.days)
		dayBar.initDayBar()
		requestNotificationPermission()
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		menuInflater.inflate(R.menu.activity_main, menu)
		return true
	}

	override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
		if (menu != null && isIgnoringBatteryOptimizations()) {
			menu.findItem(R.id.disable_battery_optimization).isVisible = false
		}
		return super.onPrepareOptionsMenu(menu)
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		when (item.itemId) {
			R.id.disable_battery_optimization -> {
				requestDisableBatteryOptimization()
				invalidateOptionsMenu()
				return true
			}
		}
		return super.onOptionsItemSelected(item)
	}

	private fun UsageGraphView.initUsageView() {
		onDayChangeChanged = {
			postUsageUpdate(dayBar.progress)
		}
		onDayChangeChange = { hour ->
			title = getString(
				R.string.day_change_at,
				String.format("%02d:00", hour)
			)
		}
		onStopTrackingTouch = {
			setTitle(R.string.app_name)
		}
	}

	private fun SeekBar.initDayBar() {
		setOnSeekBarChangeListener(object :
			SeekBar.OnSeekBarChangeListener {
			override fun onProgressChanged(
				seekBar: SeekBar,
				progress: Int,
				fromUser: Boolean
			) {
				if (fromUser) {
					val d = progress + 1
					title = resources.getQuantityString(
						R.plurals.show_x_days, d, d
					)
					postUsageUpdate(progress)
				}
			}

			override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

			override fun onStopTrackingTouch(seekBar: SeekBar) {
				setTitle(R.string.app_name)
			}
		})
		progress = prefs.graphRange
	}

	override fun onResume() {
		super.onResume()
		// Post to run update() after layout.
		postUsageUpdate(dayBar.progress)
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
		prefs.graphRange = dayBar.progress
	}

	private fun update(
		days: Int,
		timestamp: Long = System.currentTimeMillis()
	) {
		val dp = resources.displayMetrics.density
		val padding = (16f * dp).roundToInt() * 2
		val width = usageView.measuredWidth - padding
		val height = usageView.measuredHeight - padding
		if (width < 1 || height < 1) {
			postUsageUpdate(days)
			return
		}
		val chart = getUsageChart(width, height) ?: return
		val d = days + 1
		val daysString = resources.getQuantityString(R.plurals.days, d, d)
		scope.launch {
			val max = min(30, db.getAvailableHistoryInDays())
			val bitmap = chart.draw(timestamp, days, daysString)
			withContext(Dispatchers.Main) {
				usageView.setGraphBitmap(bitmap)
				if (dayBar.max != max) {
					dayBar.max = max
				}
				if (!paused) {
					postUsageUpdate(dayBar.progress, msToNextFullMinute())
				}
			}
		}
	}

	private fun getUsageChart(width: Int, height: Int): UsageChart? {
		if (usageChart == null ||
			usageChart?.width != width ||
			usageChart?.height != height
		) {
			usageChart = UsageChart(
				width,
				height,
				usagePaint,
				dialPaint,
				textPaint
			)
		}
		return usageChart
	}

	private fun postUsageUpdate(days: Int, delay: Long = 100L) {
		cancelUsageUpdate()
		updateUsageRunnable = Runnable {
			update(days)
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
