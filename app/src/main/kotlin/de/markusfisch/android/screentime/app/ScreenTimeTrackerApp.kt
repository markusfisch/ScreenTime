package de.markusfisch.android.screentime.app

import de.markusfisch.android.screentime.data.Database
import de.markusfisch.android.screentime.receiver.ScreenReceiver

import android.app.Application
import android.content.Intent
import android.content.IntentFilter

val db = Database()

class ScreenTimeTrackerApp : Application() {
	override fun onCreate() {
		super.onCreate()
		db.open(this)
		val filter = IntentFilter()
		filter.addAction(Intent.ACTION_SCREEN_ON)
		filter.addAction(Intent.ACTION_SCREEN_OFF)
		registerReceiver(ScreenReceiver(), filter);
	}
}
