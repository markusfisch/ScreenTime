package de.markusfisch.android.screentime.activity

import de.markusfisch.android.screentime.app.db
import de.markusfisch.android.screentime.data.Stats
import de.markusfisch.android.screentime.R

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class MainActivity : Activity() {
	private val updateTimeRunnable = Runnable {
		scheduleTimeUpdate()
		updateTime()
	}

	private lateinit var timeView: TextView
	private lateinit var countView: TextView
	private lateinit var stats: Stats
	private var paused = true

	override fun onCreate(state: Bundle?) {
		super.onCreate(state)
		setContentView(R.layout.activity_main)
		timeView = findViewById(R.id.time)
		countView = findViewById(R.id.count)
	}

	override fun onResume() {
		super.onResume()
		paused = false
		update()
	}

	override fun onPause() {
		super.onPause()
		paused = true
		cancelTimeUpdate()
	}

	private fun update() {
		GlobalScope.launch {
			val s = db.getStatsOfDay(System.currentTimeMillis())
			GlobalScope.launch(Main) {
				stats = s
				if (!paused) {
					updateTime()
					updateCount()
					scheduleTimeUpdate()
				}
			}
		}
	}

	private fun updateTime() {
		val seconds = stats.currentlyInSeconds(System.currentTimeMillis())
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
			stats.count,
			stats.averageColloquial()
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
