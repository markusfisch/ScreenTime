package de.markusfisch.android.screentime.database

import android.app.Activity
import de.markusfisch.android.screentime.app.db
import de.markusfisch.android.screentime.io.writeExternalFile

fun Activity.exportDatabase(fileName: String): Boolean =
	writeExternalFile(fileName, "application/vnd.sqlite3") {
		db.writeSnapshot(this, it)
	}
