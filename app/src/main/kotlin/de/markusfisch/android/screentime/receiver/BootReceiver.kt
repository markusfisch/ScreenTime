package de.markusfisch.android.screentime.receiver

import de.markusfisch.android.screentime.service.TrackerService

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
	override fun onReceive(context: Context, intent: Intent?) {
		if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
			context.startService(Intent(context, TrackerService::class.java))
		}
	}
}
