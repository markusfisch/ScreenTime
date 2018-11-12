package de.markusfisch.android.screentime.service

import de.markusfisch.android.screentime.app.db
import de.markusfisch.android.screentime.receiver.SCREEN_STATE

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileNotFoundException

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
				saveFrom(this, from)
			} else {
				if (from == 0L) {
					from = restoreFrom(this)
				}
				db.insertTime(from, System.currentTimeMillis())
			}
		}
		return Service.START_NOT_STICKY
	}
}

const val FROM_FILE = "from";

fun saveFrom(context: Context, from: Long) {
	try {
		DataOutputStream(
			context.openFileOutput(FROM_FILE, Context.MODE_PRIVATE)
		).writeLong(from)
	} catch (e: FileNotFoundException) {
		// can never happen
	}
}

fun restoreFrom(context: Context): Long {
	return try {
		DataInputStream(context.openFileInput(FROM_FILE)).readLong()
	} catch (e: FileNotFoundException) {
		System.currentTimeMillis()
	}
}
