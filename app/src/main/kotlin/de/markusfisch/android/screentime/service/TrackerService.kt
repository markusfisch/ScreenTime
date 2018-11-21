package de.markusfisch.android.screentime.service

import de.markusfisch.android.screentime.app.db
import de.markusfisch.android.screentime.data.Database
import de.markusfisch.android.screentime.notification.createNotification
import de.markusfisch.android.screentime.notification.getDefaultIntent
import de.markusfisch.android.screentime.receiver.SCREEN_STATE
import de.markusfisch.android.screentime.receiver.TIMESTAMP
import de.markusfisch.android.screentime.receiver.ScreenReceiver
import de.markusfisch.android.screentime.R

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.os.PowerManager

import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

val screenReceiver = ScreenReceiver()

class TrackerService : Service() {
	private val handler = Handler()
	private val updateNotificationRunnable = Runnable {
		scheduleNotificationUpdate()
		updateNotification()
	}

	private lateinit var notificationManager: NotificationManager
	private lateinit var powerManager: PowerManager

	override fun onCreate() {
		super.onCreate()

		val filter = IntentFilter()
		filter.addAction(Intent.ACTION_USER_PRESENT)
		filter.addAction(Intent.ACTION_SCREEN_OFF)
		registerReceiver(screenReceiver, filter)

		notificationManager = getSystemService(
			Context.NOTIFICATION_SERVICE
		) as NotificationManager
		powerManager = getSystemService(
			Context.POWER_SERVICE
		) as PowerManager

		GlobalScope.launch {
			val notification = createNotification(this@TrackerService)
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
		if (intent?.hasExtra(SCREEN_STATE) == true &&
			intent.hasExtra(TIMESTAMP)
		) {
			val screenOn = intent.getBooleanExtra(SCREEN_STATE, true)
			db.insertEvent(
				intent.getLongExtra(TIMESTAMP, System.currentTimeMillis()),
				if (screenOn) {
					Database.EVENT_SCREEN_ON
				} else {
					Database.EVENT_SCREEN_OFF
				}
			)
			if (screenOn) {
				updateNotification()
				scheduleNotificationUpdate()
			} else {
				cancelNotificationUpdate()
			}
		}
		return Service.START_STICKY
	}

	private fun cancelNotificationUpdate() {
		handler.removeCallbacks(updateNotificationRunnable)
	}

	private fun scheduleNotificationUpdate() {
		cancelNotificationUpdate()
		if (powerManager.isInteractive) {
			handler.postDelayed(
				updateNotificationRunnable,
				60000L - System.currentTimeMillis() % 60000L
			)
		}
	}

	private fun updateNotification() {
		GlobalScope.launch {
			val notification = createNotification(this@TrackerService)
			GlobalScope.launch(Main) {
				notificationManager.notify(ID, notification)
			}
		}
	}

	companion object {
		const val ID = 1
	}
}

fun createNotification(context: Context): Notification {
	val now = System.currentTimeMillis()
	val stats = db.getStatsOfDay(now)
	val text = String.format(
		context.getString(R.string.notification_text_template),
		stats.count,
		stats.averageForHumans()
	)
	return createNotification(
		context,
		R.drawable.notify,
		stats.durationForHumans(now),
		text,
		getDefaultIntent(context)
	)
}
