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
import de.markusfisch.android.screentime.data.Database
import de.markusfisch.android.screentime.notification.buildNotification
import de.markusfisch.android.screentime.receiver.*
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

val screenReceiver = EventReceiver()

fun startTrackerService(
	context: Context,
	callback: ((intent: Intent) -> Any)? = null
) {
	val intent = Intent(context, TrackerService::class.java)
	callback?.let {
		callback(intent)
	}
	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
		context.startForegroundService(intent)
	} else {
		context.startService(intent)
	}
}

class TrackerService : Service() {
	private val handler = Handler()
	private val updateNotificationRunnable = Runnable {
		updateNotification()
	}

	private lateinit var notificationManager: NotificationManager
	private lateinit var powerManager: PowerManager

	override fun onCreate() {
		super.onCreate()

		notificationManager = getSystemService(
			Context.NOTIFICATION_SERVICE
		) as NotificationManager
		powerManager = getSystemService(
			Context.POWER_SERVICE
		) as PowerManager

		val filter = IntentFilter()
		filter.addAction(Intent.ACTION_USER_PRESENT)
		filter.addAction(Intent.ACTION_SCREEN_OFF)
		registerReceiver(screenReceiver, filter)

		GlobalScope.launch {
			val notification = buildNotification(this@TrackerService)
			GlobalScope.launch(Main) {
				startForeground(ID, notification)
			}
		}
	}

	override fun onDestroy() {
		super.onDestroy()
		unregisterReceiver(screenReceiver)
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
			db.insertEvent(
				intent.getLongExtra(TIMESTAMP, System.currentTimeMillis()),
				if (screenOn) {
					Database.EVENT_SCREEN_ON
				} else {
					Database.EVENT_SCREEN_OFF
				},
				intent.getFloatExtra(BATTERY_LEVEL, 0f)
			)
			if (screenOn) {
				updateNotification()
			} else {
				cancelNotificationUpdate()
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
		GlobalScope.launch {
			val notification = buildNotification(this@TrackerService, true)
			GlobalScope.launch(Main) {
				notificationManager.notify(ID, notification)
			}
		}
	}

	private fun buildNotification(
		context: Context,
		schedule: Boolean = false
	): Notification {
		val now = System.currentTimeMillis()
		val stats = db.getStatsOfDay(now)
		if (schedule) {
			// calculate milliseconds until the minute value changes
			scheduleNotificationUpdate(60000L - stats.currently(now) % 60000L)
		}
		return buildNotification(
			context,
			R.drawable.ic_notify,
			stats.currentlyColloquial(now),
			Intent(context, MainActivity::class.java)
		)
	}

	companion object {
		const val ID = 1
	}
}
