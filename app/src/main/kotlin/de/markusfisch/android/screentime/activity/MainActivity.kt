package de.markusfisch.android.screentime.activity

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import de.markusfisch.android.screentime.R
import de.markusfisch.android.screentime.app.PERMISSION_WRITE
import de.markusfisch.android.screentime.app.db
import de.markusfisch.android.screentime.app.prefs
import de.markusfisch.android.screentime.app.requestNotificationPermission
import de.markusfisch.android.screentime.app.requestWritePermission
import de.markusfisch.android.screentime.app.runPermissionCallback
import de.markusfisch.android.screentime.database.exportDatabase
import de.markusfisch.android.screentime.database.importDatabase
import de.markusfisch.android.screentime.dialog.askForName
import de.markusfisch.android.screentime.graphics.UsageChart
import de.markusfisch.android.screentime.graphics.loadColor
import de.markusfisch.android.screentime.os.isIgnoringBatteryOptimizations
import de.markusfisch.android.screentime.os.requestDisableBatteryOptimization
import de.markusfisch.android.screentime.service.msToNextFullMinute
import de.markusfisch.android.screentime.widget.UsageGraphView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
	private lateinit var progressView: View

	private var updateUsageRunnable: Runnable? = null
	private var usageChart: UsageChart? = null
	private var paused = true

	override fun onRequestPermissionsResult(
		requestCode: Int,
		permissions: Array<String>,
		grantResults: IntArray
	) {
		when (requestCode) {
			PERMISSION_WRITE -> if (grantResults.isNotEmpty() &&
				grantResults[0] == PackageManager.PERMISSION_GRANTED
			) {
				runPermissionCallback()
			}
		}
	}

	override fun onActivityResult(
		requestCode: Int, resultCode: Int,
		resultData: Intent?
	) {
		if (requestCode == PICK_FILE_RESULT_CODE &&
			resultCode == RESULT_OK &&
			resultData != null
		) {
			updateProgress(R.string.importing)
			scope.launch {
				val message = importDatabase(resultData.data)
				withContext(Dispatchers.Main) {
					updateProgress()
					Toast.makeText(
						this@MainActivity,
						message,
						Toast.LENGTH_LONG
					).show()
				}
			}
		}
	}

	override fun onCreate(state: Bundle?) {
		super.onCreate(state)
		setContentView(R.layout.activity_main)
		usageView = findViewById(R.id.graph)
		usageView.initUsageView()
		dayBar = findViewById(R.id.days)
		dayBar.initDayBar()
		progressView = findViewById(R.id.progress_view)
		requestNotificationPermission()
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		menuInflater.inflate(R.menu.activity_main, menu)
		return true
	}

	override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
		menu?.let {
			val visibility = progressView.visibility == View.GONE
			arrayOf(
				R.id.show_multiday,
				R.id.disable_battery_optimization,
				R.id.import_export_database
			).forEach {
				menu.findItem(it).isVisible = if (
					it == R.id.disable_battery_optimization &&
					isIgnoringBatteryOptimizations()
				) false else visibility
			}
		}
		return super.onPrepareOptionsMenu(menu)
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		return when (item.itemId) {
			R.id.show_multiday -> {
				startActivity(Intent(this, MultidayActivity::class.java))
				true
			}

			R.id.disable_battery_optimization -> {
				requestDisableBatteryOptimization()
				true
			}

			R.id.import_export_database -> {
				askExportORImport()
				true
			}

			else -> super.onOptionsItemSelected(item)
		}
	}

	private fun askExportORImport() {
		AlertDialog.Builder(this).setItems(
			R.array.import_export
		) { dialog, which ->
			when (which) {
				0 -> askForFileToImport()
				else -> askToExportToFile()
			}
			dialog.dismiss()
		}.show()
	}

	private fun askForFileToImport() {
		startActivityForResult(
			Intent.createChooser(
				Intent(Intent.ACTION_GET_CONTENT).apply {
					// In theory, it should be "application/x-sqlite3"
					// or the newer "application/vnd.sqlite3" but
					// only "application/octet-stream" works.
					type = "application/octet-stream"
				},
				getString(R.string.import_database)
			),
			PICK_FILE_RESULT_CODE
		)
	}

	private fun askToExportToFile() {
		// Write permission is only required before Android Q.
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
			!requestWritePermission() { askToExportToFile() }
		) {
			return
		}
		askForName(R.string.save_as, "screen_time.db") { name ->
			updateProgress(R.string.exporting)
			scope.launch {
				val messageId = if (exportDatabase(name)) {
					R.string.export_successful
				} else {
					R.string.export_failed
				}
				withContext(Dispatchers.Main) {
					updateProgress()
					Toast.makeText(
						this@MainActivity,
						messageId,
						Toast.LENGTH_LONG
					).show()
				}
			}
		}
	}

	private fun updateProgress(messageId: Int = 0) {
		progressView.visibility = if (messageId > 0) {
			setTitle(messageId)
			View.VISIBLE
		} else {
			setTitle(R.string.app_name)
			View.GONE
		}
		invalidateOptionsMenu()
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
		// To update the options menu after enabling battery optimization.
		invalidateOptionsMenu()
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

	companion object {
		private const val PICK_FILE_RESULT_CODE = 1
	}
}

private fun fillPaint(col: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
	color = col
	style = Paint.Style.FILL
}
