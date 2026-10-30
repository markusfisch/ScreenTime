package de.markusfisch.android.screentime.service

import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.os.UserManager
import de.markusfisch.android.screentime.R
import de.markusfisch.android.screentime.activity.MainActivity
import de.markusfisch.android.screentime.app.db
import de.markusfisch.android.screentime.database.timeRangeColloquial
import de.markusfisch.android.screentime.os.UsageTracking
import de.markusfisch.android.screentime.os.TrackingStatus
import de.markusfisch.android.screentime.notification.buildNotification
import de.markusfisch.android.screentime.receiver.ACTION
import de.markusfisch.android.screentime.receiver.EventReceiver
import de.markusfisch.android.screentime.receiver.TIMESTAMP
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val eventReceiver = EventReceiver()

var trackingStatus: TrackingStatus? = null
	private set

fun Context.startTrackerService(
	callback: ((intent: Intent) -> Any)? = null
) {
	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
		!(getSystemService(Context.USER_SERVICE) as UserManager).isUserUnlocked
	) {
		return
	}
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
	private val scope = CoroutineScope(Dispatchers.Main.immediate + job)
	private val handler by lazy { Handler(mainLooper) }
	private val updateNotificationRunnable = Runnable {
		updateTracking()
	}

	private lateinit var notificationManager: NotificationManager
	private lateinit var powerManager: PowerManager

	private val usageTracking by lazy { UsageTracking(this) }
	private var notificationJob: Job? = null

	private var eventId: Long = 0L
	private var uptimeFrom: Long = 0L
	private var lastNotification: Long = 0L

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
		filter.addAction(Intent.ACTION_TIME_CHANGED)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
			filter.addAction(Intent.ACTION_DREAMING_STARTED)
		}
		registerReceiver(eventReceiver, filter)

		val notification = buildNotification(
			R.drawable.ic_notify, getString(R.string.recording),
			Intent(this, MainActivity::class.java)
		)
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
			startForeground(NOTIFICATION_ID, notification)
		} else {
			startForeground(
				NOTIFICATION_ID, notification,
				ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
			)
		}
	}

	override fun onDestroy() {
		super.onDestroy()
		trackingStatus = null
		unregisterReceiver(eventReceiver)
		cancelNotificationUpdate()
		job.cancel()
	}

	override fun onBind(intent: Intent): IBinder? {
		return null
	}

	override fun onStartCommand(
		intent: Intent?,
		flags: Int,
		startId: Int
	): Int {
		updateTracking(intent)
		return START_STICKY
	}

	private fun updateTracking(intent: Intent? = null) {
		val now = System.currentTimeMillis()
		val previous = trackingStatus
		trackingStatus = usageTracking.update(now, isInteractive())
		if (trackingStatus == TrackingStatus.ANDROID) {
			if (eventId > 0L) {
				updateEvent(eventId)
			}
			eventId = 0L
		} else {
			if ((previous == null || previous == TrackingStatus.ANDROID) && isInteractive()) {
				insertScreenEvent(now, true)
			}
			if (intent != null) {
				handleScreenEvent(intent)
			}
			if (eventId > 0L) {
				updateEvent(eventId)
			}
		}
		scheduleNotificationUpdate(msToNextFullMinute(now))
		updateNotification(now)
	}

	private fun handleScreenEvent(intent: Intent) {
		when (intent.getStringExtra(ACTION)) {
			// Some devices send SCREEN_OFF immediately followed by SCREEN_ON
			// when launching the camera. Ignore it while still interactive.
			Intent.ACTION_SCREEN_OFF -> if (!isInteractive()) {
				intent.insertScreenEvent(false)
			}
			Intent.ACTION_SHUTDOWN,
			QUICKBOOT_POWER_OFF,
			QUICKBOOT_POWER_OFF_HTC,
			Intent.ACTION_DREAMING_STARTED -> intent.insertScreenEvent(false)
			Intent.ACTION_USER_PRESENT -> intent.insertScreenEvent(true)
			Intent.ACTION_BOOT_COMPLETED -> if (isInteractive()) {
				intent.insertScreenEvent(true)
			}
		}
	}

	private fun Intent.insertScreenEvent(screenOn: Boolean) {
		insertScreenEvent(
			getLongExtra(TIMESTAMP, System.currentTimeMillis()),
			screenOn
		)
	}

	private fun insertScreenEvent(timestamp: Long, screenOn: Boolean) {
		if (screenOn) {
			val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
			val locked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
				keyguard.isKeyguardLocked
			} else {
				@Suppress("DEPRECATION")
				keyguard.inKeyguardRestrictedInputMode()
			}
			if (locked) {
				return
			}
			if (eventId == 0L) {
				eventId = db.insertEvent(timestamp)
				uptimeFrom = SystemClock.uptimeMillis()
			}
		} else {
			cancelNotificationUpdate()
			if (eventId > 0L) {
				updateEvent(eventId)
			}
			eventId = 0L
		}
	}

	private fun cancelNotificationUpdate() {
		handler.removeCallbacks(updateNotificationRunnable)
	}

	private fun scheduleNotificationUpdate(delay: Long) {
		scope.launch(Dispatchers.Main.immediate) {
			cancelNotificationUpdate()
			if (isInteractive() && (trackingStatus == TrackingStatus.ANDROID || eventId > 0L)) {
				handler.postDelayed(updateNotificationRunnable, delay)
			}
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

	private fun updateNotification(now: Long) {
		val elapsed = SystemClock.elapsedRealtime()
		if (elapsed - lastNotification < 500L) {
			return
		}
		lastNotification = elapsed
		notificationJob?.cancel()
		notificationJob = scope.launch {
			val total = withContext(Dispatchers.IO) { db.summarizeDay(now) }
			val notification = buildNotification(
				R.drawable.ic_notify,
				timeRangeColloquial(total / 1000L),
				Intent(this@TrackerService, MainActivity::class.java)
			)
			notificationManager.notify(NOTIFICATION_ID, notification)
		}
	}

	private fun updateEvent(eventId: Long) {
		db.updateEvent(eventId, SystemClock.uptimeMillis() - uptimeFrom)
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
