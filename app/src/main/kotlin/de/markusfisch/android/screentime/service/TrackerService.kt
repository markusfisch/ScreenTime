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
import android.os.IBinder

import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

val screenReceiver = ScreenReceiver()

class TrackerService : Service() {
	private lateinit var notificationMan: NotificationManager

	override fun onCreate() {
		super.onCreate()
		val filter = IntentFilter()
		filter.addAction(Intent.ACTION_SCREEN_ON)
		filter.addAction(Intent.ACTION_SCREEN_OFF)
		registerReceiver(screenReceiver, filter)
		notificationMan = getSystemService(
			Context.NOTIFICATION_SERVICE
		) as NotificationManager
		GlobalScope.launch() {
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
			}
		}
		return Service.START_STICKY
	}

	private fun updateNotification() {
		GlobalScope.launch() {
			val notification = createNotification(this@TrackerService)
			GlobalScope.launch(Main) {
				notificationMan.notify(ID, notification)
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
	val seconds = (stats.millisecs + now - stats.start) / 1000
	val title = String.format(
		"%02d:%02d",
		seconds / 3600,
		(seconds / 60) % 60
	)
	val text = String.format(
		context.getString(R.string.notification_text_template),
		stats.count,
		stats.average
	)
	return createNotification(
		context,
		R.drawable.notify,
		title,
		text,
		getDefaultIntent(context)
	)
}
