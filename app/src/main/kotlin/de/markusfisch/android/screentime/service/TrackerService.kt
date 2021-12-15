package de.markusfisch.android.screentime.service

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.PowerManager
import de.markusfisch.android.screentime.R
import de.markusfisch.android.screentime.activity.MainActivity
import de.markusfisch.android.screentime.app.db
import de.markusfisch.android.screentime.data.Summary
import de.markusfisch.android.screentime.data.summarizeDay
import de.markusfisch.android.screentime.notification.buildNotification
import de.markusfisch.android.screentime.receiver.*
import kotlinx.coroutines.*
import java.lang.Runnable

val screenReceiver = EventReceiver()

fun Context.startTrackerService(
	callback: ((intent: Intent) -> Any)? = null
) {
	val intent = Intent(this, TrackerService::class.java)
	callback?.let {
		callback(intent)
	}
	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
		startForegroundService(intent)
	} else {
		startService(intent)
	}
}

class TrackerService : Service() {
	private val job = SupervisorJob()
	private val scope = CoroutineScope(Dispatchers.Default + job)
	private val handler by lazy { Handler(mainLooper) }
	private val updateNotificationRunnable = Runnable {
		updateNotification()
	}

	private lateinit var notificationManager: NotificationManager
	private lateinit var powerManager: PowerManager

	private var summary: Summary? = null
	private var unlocked = false

	override fun onCreate() {
		super.onCreate()

		notificationManager = getSystemService(
			Context.NOTIFICATION_SERVICE
		) as NotificationManager
		powerManager = getSystemService(
			Context.POWER_SERVICE
		) as PowerManager
		unlocked = isInteractive()

		val filter = IntentFilter()
		filter.addAction(Intent.ACTION_SCREEN_ON)
		filter.addAction(Intent.ACTION_SCREEN_OFF)
		filter.addAction(Intent.ACTION_USER_PRESENT)
		registerReceiver(screenReceiver, filter)

		scope.launch {
			val notification = buildNotification()
			withContext(Dispatchers.Main) {
				startForeground(ID, notification)
			}
		}
	}

	override fun onDestroy() {
		super.onDestroy()
		unregisterReceiver(screenReceiver)
		job.cancelChildren()
	}

	override fun onBind(intent: Intent): IBinder? {
		return null
	}

	override fun onStartCommand(
		intent: Intent?,
		flags: Int,
		startId: Int
	): Int {
		if (intent?.hasExtra(UPDATE_NOTIFICATION) == true) {
			updateNotification()
		} else if (intent?.hasExtra(SCREEN_STATE) == true &&
			intent.hasExtra(TIMESTAMP)
		) {
			val screenOn = intent.getBooleanExtra(SCREEN_STATE, true)
			// Ignore ACTION_SCREEN_OFF events when the device is still
			// interactive (e.g. the camera app was opened by double pressing
			// on/off) or wasn't interactive before (e.g. the screen was turned
			// on but never unlocked).
			if (!screenOn && (isInteractive() || !unlocked)) {
				return START_STICKY
			}
			db.insertScreenEvent(
				intent.getLongExtra(TIMESTAMP, System.currentTimeMillis()),
				screenOn,
				intent.getFloatExtra(BATTERY_LEVEL, 0f)
			)
			if (screenOn) {
				updateNotification()
				unlocked = true
			} else {
				cancelNotificationUpdate()
				summary = null
				unlocked = false
			}
		}
		return START_STICKY
	}

	private fun cancelNotificationUpdate() {
		handler.removeCallbacks(updateNotificationRunnable)
	}

	private fun scheduleNotificationUpdate(delay: Long) {
		cancelNotificationUpdate()
		if (isInteractive()) {
			handler.postDelayed(updateNotificationRunnable, delay)
		}
	}

	private fun isInteractive() = if (
		Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH
	) {
		powerManager.isInteractive
	} else {
		@Suppress("DEPRECATION")
		powerManager.isScreenOn
	}

	private fun updateNotification() {
		scope.launch {
			val notification = buildNotification(true)
			withContext(Dispatchers.Main) {
				notificationManager.notify(ID, notification)
			}
		}
	}

	private fun Context.buildNotification(
		schedule: Boolean = false
	): Notification {
		val now = System.currentTimeMillis()
		val sum = summary ?: summarizeDay(now)
		summary = sum
		if (schedule) {
			scheduleNotificationUpdate(msToNextFullMinute(now))
		}
		return buildNotification(
			R.drawable.ic_notify,
			sum.currentlyColloquial(now),
			Intent(this, MainActivity::class.java)
		)
	}

	companion object {
		const val ID = 1
	}
}

// Calculate the time to the next full minute.
fun msToNextFullMinute(
	now: Long = System.currentTimeMillis()
): Long = 60000L - now % 60000L
