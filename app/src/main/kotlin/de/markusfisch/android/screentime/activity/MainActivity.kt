package de.markusfisch.android.screentime.activity

import android.app.Activity
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import de.markusfisch.android.screentime.R
import de.markusfisch.android.screentime.data.Summary
import de.markusfisch.android.screentime.data.drawUsageChart
import de.markusfisch.android.screentime.data.summarizeDay
import kotlinx.coroutines.*
import java.lang.Runnable
import kotlin.coroutines.CoroutineContext
import kotlin.math.roundToInt

class MainActivity : Activity(), CoroutineScope {
	override val coroutineContext: CoroutineContext
		get() = Dispatchers.IO + job

	private val job = SupervisorJob()
	private val updateTimeRunnable = Runnable {
		scheduleTimeUpdate()
		updateTime()
	}

	private lateinit var usageView: ImageView
	private lateinit var timeView: TextView
	private lateinit var countView: TextView
	private lateinit var summary: Summary
	private var paused = true

	override fun onCreate(state: Bundle?) {
		super.onCreate(state)
		setContentView(R.layout.activity_main)
		usageView = findViewById(R.id.graph)
		timeView = findViewById(R.id.time)
		countView = findViewById(R.id.count)
	}

	override fun onResume() {
		super.onResume()
		paused = false
		// Make sure this runs after layout.
		usageView.post {
			update()
		}
	}

	override fun onPause() {
		super.onPause()
		paused = true
		cancelTimeUpdate()
	}

	override fun onDestroy() {
		super.onDestroy()
		coroutineContext.cancelChildren()
	}

	private fun update() {
		launch {
			generateSummary()
			generateUsageChart()
		}
	}

	private suspend fun generateSummary() = withContext(Dispatchers.IO) {
		val s = summarizeDay()
		withContext(Dispatchers.Main) {
			summary = s
			if (!paused) {
				updateTime()
				updateCount()
				scheduleTimeUpdate()
			}
		}
	}

	private suspend fun generateUsageChart() = withContext(Dispatchers.IO) {
		val width = usageView.measuredWidth
		val height = usageView.measuredHeight
		if (width < 1 || height < 1) {
			usageView.postDelayed({
				update()
			}, 1000)
			return@withContext
		}
		val dp = resources.displayMetrics.density
		val padding = (16f * dp).roundToInt() * 2
		val bitmap = drawUsageChart(
			width - padding,
			height - padding,
			7,
			resources.getColor(R.color.primary_dark),
			resources.getColor(R.color.dial),
			resources.getColor(R.color.primary_dark),
			dp
		)
		withContext(Dispatchers.Main) {
			usageView.setImageBitmap(bitmap)
		}
	}

	private fun updateTime() {
		val seconds = summary.currentlyInSeconds(System.currentTimeMillis())
		timeView.text = String.format(
			"%02d:%02d:%02d",
			seconds / 3600,
			(seconds / 60) % 60,
			seconds % 60
		)
	}

	private fun updateCount() {
		countView.text = getString(
			R.string.count,
			summary.count,
			summary.averageColloquial()
		)
	}

	private fun scheduleTimeUpdate() {
		cancelTimeUpdate()
		timeView.postDelayed(updateTimeRunnable, 1000)
	}

	private fun cancelTimeUpdate() {
		timeView.removeCallbacks(updateTimeRunnable)
	}
}
