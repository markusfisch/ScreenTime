package de.markusfisch.android.screentime.app

import de.markusfisch.android.screentime.data.Database
import de.markusfisch.android.screentime.service.startTrackerService

import android.app.Application

val db = Database()

class ScreenTimeTrackerApp : Application() {
	override fun onCreate() {
		super.onCreate()
		db.open(this)
		startTrackerService(this)
	}
}
