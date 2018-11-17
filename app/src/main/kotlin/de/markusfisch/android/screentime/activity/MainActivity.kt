package de.markusfisch.android.screentime.activity

import de.markusfisch.android.screentime.app.db
import de.markusfisch.android.screentime.data.Stats
import de.markusfisch.android.screentime.R

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch

class MainActivity() : Activity() {
	private val updateTimeRunnable = Runnable {
		updateTime()
		scheduleTimeUpdate()
	}

	private lateinit var timeView: TextView
	private lateinit var countView: TextView
	private lateinit var stats: Stats

	override fun onCreate(state: Bundle?) {
		super.onCreate(state)
		setContentView(R.layout.activity_main)
		timeView = findViewById(R.id.time)
		countView = findViewById(R.id.count)
	}

	override fun onResume() {
		super.onResume()
		update()
	}

	override fun onPause() {
		super.onPause()
		cancelTimeUpdate()
	}

	private fun update() {
		launch {
			val s = db.getStatsOfDay(System.currentTimeMillis())
			launch(UI) {
				stats = s
				updateTime()
				updateCount()
				scheduleTimeUpdate()
			}
		}
	}

	private fun updateTime() {
		val now = System.currentTimeMillis()
		if (db.from == 0L) {
			db.from = now
		}
		timeView.text = hoursAndSeconds(
			stats.millisecs + now - db.from
		)
	}

	private fun hoursAndSeconds(ms: Long): String {
		val seconds = ms / 1000;
		return String.format(
			"%02d:%02d:%02d",
			seconds / 3600,
			(seconds / 60) % 60,
			seconds % 60
		)
	}

	private fun updateCount() {
		countView.text = String.format(
			getString(R.string.count),
			stats.count,
			Math.round(
				stats.millisecs.toFloat() / stats.count.toFloat() / 1000f
			)
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
