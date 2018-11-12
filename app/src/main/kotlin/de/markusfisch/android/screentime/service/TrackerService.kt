package de.markusfisch.android.screentime.service

import de.markusfisch.android.screentime.app.db
import de.markusfisch.android.screentime.receiver.SCREEN_STATE

import android.app.Service
import android.content.Intent
import android.os.IBinder

class TrackerService() : Service() {
	private var from = 0L

	override fun onBind(intent: Intent): IBinder? {
		return null
	}

	override fun onStartCommand(
		intent: Intent?,
		flags: Int,
		startId: Int
	): Int {
		if (intent != null && intent.hasExtra(SCREEN_STATE)) {
			if (intent.getBooleanExtra(SCREEN_STATE, true)) {
				from = System.currentTimeMillis()
			} else if (from > 0L) {
				db.insertTime(from, System.currentTimeMillis())
			}
		}
		return Service.START_NOT_STICKY
	}
}
