package de.markusfisch.android.screentime.app

import android.app.Application
import de.markusfisch.android.screentime.data.Database
import de.markusfisch.android.screentime.preference.Preferences
import de.markusfisch.android.screentime.service.startTrackerService

val db = Database()
val prefs = Preferences()

class ScreenTimeTrackerApp : Application() {
	override fun onCreate() {
		super.onCreate()
		db.open(this)
		prefs.init(this)
		startTrackerService()
	}
}
