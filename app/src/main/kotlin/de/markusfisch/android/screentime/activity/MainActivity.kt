package de.markusfisch.android.screentime.activity

import android.app.Activity
import android.os.Bundle
import android.preference.PreferenceManager.getDefaultSharedPreferences
import android.view.View
import android.widget.ImageView
import android.widget.SeekBar
import de.markusfisch.android.screentime.R
import de.markusfisch.android.screentime.app.db
import de.markusfisch.android.screentime.data.drawUsageChart
import de.markusfisch.android.screentime.service.msToNextFullMinute
import kotlinx.coroutines.*
import kotlin.math.min
import kotlin.math.roundToInt

class MainActivity : Activity() {
	private val job = SupervisorJob()
	private val scope = CoroutineScope(Dispatchers.Default + job)
	private val updateUsageRunnable = Runnable {
		scheduleUsageUpdate()
		update(dayBar.progress)
	}
	private val prefs by lazy { getDefaultSharedPreferences(this) }

	private lateinit var usageView: ImageView
	private lateinit var dayBar: SeekBar
	private var paused = true

	override fun onCreate(state: Bundle?) {
		super.onCreate(state)
		setContentView(R.layout.activity_main)
		usageView = findViewById(R.id.graph)
		dayBar = findViewById(R.id.days)

		val availableHistoryInDays = db.availableHistoryInDays
		if (availableHistoryInDays < 1) {
			// Insert an initial SCREEN ON event if the database is
			// empty because we can only find an empty database if
			// the user has started this app for the first time.
			db.insertScreenEvent(System.currentTimeMillis(), true, 0f)
		}
		dayBar.progress = prefs.getInt(DAYS, dayBar.progress)
		dayBar.max = min(30, availableHistoryInDays)
		dayBar.setOnSeekBarChangeListener(object :
			SeekBar.OnSeekBarChangeListener {
			override fun onProgressChanged(
				seekBar: SeekBar,
				progress: Int,
				fromUser: Boolean
			) {
				if (fromUser) {
					// Post to queue changes.
					dayBar.post {
						update(progress)
					}
				}
			}

			override fun onStartTrackingTouch(seekBar: SeekBar) {}

			override fun onStopTrackingTouch(seekBar: SeekBar) {}
		})

		if (dayBar.max == 0) {
			dayBar.visibility = View.GONE
		}
	}

	override fun onResume() {
		super.onResume()
		paused = false
		// Run update() after layout.
		usageView.post {
			update(dayBar.progress)
		}
	}

	override fun onPause() {
		super.onPause()
		paused = true
		cancelUsageUpdate()
		prefs.edit().apply {
			putInt(DAYS, dayBar.progress)
			apply()
		}
	}

	override fun onDestroy() {
		super.onDestroy()
		job.cancelChildren()
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
			usageView.postDelayed({
				update(days, timestamp)
			}, 1000)
			return
		}
		scope.launch {
			val d = days + 1
			val bitmap = drawUsageChart(
				width,
				height,
				timestamp,
				days,
				resources.getQuantityString(R.plurals.days, d, d),
				resources.getColor(R.color.usage),
				resources.getColor(R.color.dial),
				resources.getColor(R.color.text)
			)
			withContext(Dispatchers.Main) {
				usageView.setImageBitmap(bitmap)
				if (!paused) {
					scheduleUsageUpdate()
				}
			}
		}
	}

	private fun scheduleUsageUpdate() {
		cancelUsageUpdate()
		usageView.postDelayed(updateUsageRunnable, msToNextFullMinute())
	}

	private fun cancelUsageUpdate() {
		usageView.removeCallbacks(updateUsageRunnable)
	}

	companion object {
		private const val DAYS = "days"
	}
}
