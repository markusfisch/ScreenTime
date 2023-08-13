package de.markusfisch.android.screentime.app

import android.app.Application
import android.content.Context
import de.markusfisch.android.screentime.data.Database
import de.markusfisch.android.screentime.preference.Preferences
import de.markusfisch.android.screentime.service.startTrackerService

val db: Database by lazy {
	Database(appContext)
}
val prefs: Preferences by lazy {
	Preferences(appContext)
}

// Required for the lazy initialization of Database and Preferences.
// It's okay to keep a reference to the application context as this
// can never be garbage collected, of course.
private lateinit var appContext: Context

class ScreenTimeTrackerApp : Application() {
	override fun onCreate() {
		super.onCreate()
		appContext = this
		// It's important to not use db or prefs before the device
		// is unlocked because credential encrypted storage is not
		// available before.
		startTrackerService()
	}
}
