package de.markusfisch.android.screentime.activity

import de.markusfisch.android.screentime.app.db
import de.markusfisch.android.screentime.R

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch

class MainActivity() : Activity() {
	private lateinit var timeView : TextView
	private lateinit var countView : TextView

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

	private fun update() {
		launch {
			val stats = db.getStatsOfDay(System.currentTimeMillis())
			launch(UI) {
				timeView.text = hoursAndSeconds(stats.time)
				countView.text = String.format(
					getString(R.string.count),
					stats.count,
					Math.round(stats.time.toFloat() / stats.count.toFloat())
				)
			}
		}
	}

	private fun hoursAndSeconds(seconds: Long): String {
		return String.format(
			"%02d:%02d:%02d",
			seconds / 3600,
			seconds / 60,
			seconds % 60
		)
	}
}
