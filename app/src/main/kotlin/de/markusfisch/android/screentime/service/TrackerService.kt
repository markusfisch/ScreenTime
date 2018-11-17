package de.markusfisch.android.screentime.service

import de.markusfisch.android.screentime.app.db
import de.markusfisch.android.screentime.receiver.SCREEN_STATE
import de.markusfisch.android.screentime.receiver.ScreenReceiver

import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder

class TrackerService() : Service() {
	override fun onCreate() {
		super.onCreate()
		val filter = IntentFilter()
		filter.addAction(Intent.ACTION_SCREEN_ON)
		filter.addAction(Intent.ACTION_SCREEN_OFF)
		registerReceiver(ScreenReceiver(), filter);
	}

	override fun onBind(intent: Intent): IBinder? {
		return null
	}

	override fun onStartCommand(
		intent: Intent?,
		flags: Int,
		startId: Int
	): Int {
		if (intent != null && intent.hasExtra(SCREEN_STATE)) {
			val now = System.currentTimeMillis()
			if (intent.getBooleanExtra(SCREEN_STATE, true)) {
				db.from = now
			} else if (db.from > 0L) {
				db.insertTime(db.from, now)
				db.from = 0L
			}
		}
		return Service.START_STICKY
	}
}
