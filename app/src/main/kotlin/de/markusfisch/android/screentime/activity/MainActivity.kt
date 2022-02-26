package de.markusfisch.android.screentime.activity

import android.app.Activity
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import android.os.Bundle
import android.preference.PreferenceManager.getDefaultSharedPreferences
import android.view.View
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import de.markusfisch.android.screentime.R
import de.markusfisch.android.screentime.app.db
import de.markusfisch.android.screentime.data.UsageChart
import de.markusfisch.android.screentime.service.msToNextFullMinute
import kotlinx.coroutines.*
import kotlin.math.min
import kotlin.math.roundToInt

class MainActivity : Activity() {
	private val job = SupervisorJob()
	private val scope = CoroutineScope(Dispatchers.Default + job)
	private val prefs by lazy { getDefaultSharedPreferences(this) }
	private val usagePaint by lazy {
		fillPaint(resources.getColor(R.color.usage)).apply {
			xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
		}
	}
	private val dialPaint by lazy {
		fillPaint(resources.getColor(R.color.dial))
	}
	private val textPaint by lazy {
		fillPaint(resources.getColor(R.color.text)).apply {
			typeface = Typeface.DEFAULT_BOLD
		}
	}

	private lateinit var usageView: ImageView
	private lateinit var dayLabel: TextView
	private lateinit var dayBar: SeekBar

	private var updateUsageRunnable: Runnable? = null
	private var usageChart: UsageChart? = null
	private var paused = true

	override fun onCreate(state: Bundle?) {
		super.onCreate(state)
		setContentView(R.layout.activity_main)
		usageView = findViewById(R.id.graph)
		dayLabel = findViewById(R.id.label)
		dayBar = findViewById(R.id.days)
		dayBar.initDayBar()
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
					dayLabel.text = resources.getQuantityString(
						R.plurals.preview, d, d
					)
					postUsageUpdate(progress)
				}
			}

			override fun onStartTrackingTouch(seekBar: SeekBar) {
				setEditing(true)
			}

			override fun onStopTrackingTouch(seekBar: SeekBar) {
				setEditing(false)
			}
		})
		progress = prefs.getInt(DAYS, progress)
	}

	private fun setEditing(editing: Boolean) {
		usageView.alpha = if (editing) .1f else 1f
		dayLabel.apply {
			text = ""
			visibility = if (editing) View.VISIBLE else View.GONE
		}
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
		prefs.edit().apply {
			putInt(DAYS, dayBar.progress)
			apply()
		}
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
				usageView.setImageBitmap(bitmap)
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

	companion object {
		private const val DAYS = "days"
	}
}

private fun fillPaint(col: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
	color = col
	style = Paint.Style.FILL
}
