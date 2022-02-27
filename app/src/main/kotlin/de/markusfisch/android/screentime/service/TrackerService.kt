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
import de.markusfisch.android.screentime.data.timeRangeColloquial
import de.markusfisch.android.screentime.notification.buildNotification
import de.markusfisch.android.screentime.receiver.ACTION
import de.markusfisch.android.screentime.receiver.EventReceiver
import de.markusfisch.android.screentime.receiver.TIMESTAMP
import kotlinx.coroutines.*
import java.lang.Runnable

val eventReceiver = EventReceiver()

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

	private var eventId: Long = 0L
	private var lastNotifcation: Long = 0L

	override fun onCreate() {
		super.onCreate()

		notificationManager = getSystemService(
			Context.NOTIFICATION_SERVICE
		) as NotificationManager
		powerManager = getSystemService(
			Context.POWER_SERVICE
		) as PowerManager

		val filter = IntentFilter()
		filter.addAction(Intent.ACTION_SCREEN_ON)
		filter.addAction(Intent.ACTION_SCREEN_OFF)
		filter.addAction(Intent.ACTION_USER_PRESENT)
		filter.addAction(Intent.ACTION_SHUTDOWN)
		registerReceiver(eventReceiver, filter)

		val now = System.currentTimeMillis()
		if (isInteractive()) {
			insertScreenEvent(now, true)
		}

		scope.launch {
			val notification = buildAndScheduleNotification(now)
			withContext(Dispatchers.Main) {
				startForeground(NOTIFICATION_ID, notification)
			}
		}
	}

	override fun onDestroy() {
		super.onDestroy()
		unregisterReceiver(eventReceiver)
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
		if (intent?.hasExtra(ACTION) == true) {
			when (intent.getStringExtra(ACTION)) {
				Intent.ACTION_SCREEN_ON -> updateNotification()
				Intent.ACTION_SCREEN_OFF,
				Intent.ACTION_SHUTDOWN,
				QUICKBOOT_POWER_OFF,
				QUICKBOOT_POWER_OFF_HTC -> intent.insertScreenEvent(false)
				Intent.ACTION_USER_PRESENT -> intent.insertScreenEvent(true)
				Intent.ACTION_BOOT_COMPLETED -> if (isInteractive()) {
					// Since the app was just started by the system, we
					// have missed any previous ACTION_USER_PRESENT.
					intent.insertScreenEvent(true)
				}
			}
		}
		return START_STICKY
	}

	private fun Intent.insertScreenEvent(screenOn: Boolean) {
		insertScreenEvent(
			getLongExtra(TIMESTAMP, System.currentTimeMillis()),
			screenOn
		)
	}

	private fun insertScreenEvent(timestamp: Long, screenOn: Boolean) {
		// Ignore ACTION_SCREEN_OFF events when the device is still
		// interactive (e.g. when the camera app was opened by double
		// pressing on/off there's a ACTION_SCREEN_OFF event directly
		// followed by ACTION_SCREEN_ON).
		if (!screenOn && isInteractive()) {
			return
		}
		if (screenOn) {
			eventId = db.insertEvent(timestamp)
			updateNotification()
		} else {
			cancelNotificationUpdate()
			if (eventId > 0L) {
				db.updateEvent(eventId, timestamp)
			}
			eventId = 0L
		}
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
		val now = System.currentTimeMillis()
		if (now - lastNotifcation < 500L) {
			// Skip this update as there's likey no change.
			return
		}
		lastNotifcation = now
		scope.launch {
			val notification = buildAndScheduleNotification(now)
			withContext(Dispatchers.Main) {
				notificationManager.notify(NOTIFICATION_ID, notification)
			}
		}
	}

	private fun Context.buildAndScheduleNotification(now: Long): Notification {
		scheduleNotificationUpdate(msToNextFullMinute(now))
		if (eventId > 0L) {
			db.updateEvent(eventId, now)
		}
		return buildNotification(
			R.drawable.ic_notify,
			timeRangeColloquial(db.summarizeDay(now) / 1000L),
			Intent(this, MainActivity::class.java)
		)
	}

	companion object {
		const val NOTIFICATION_ID = 1

		private const val QUICKBOOT_POWER_OFF = "android.intent.action.QUICKBOOT_POWEROFF"
		private const val QUICKBOOT_POWER_OFF_HTC = "com.htc.intent.action.QUICKBOOT_POWEROFF"
	}
}

fun msToNextFullMinute(
	now: Long = System.currentTimeMillis()
): Long = 60000L - now % 60000L
