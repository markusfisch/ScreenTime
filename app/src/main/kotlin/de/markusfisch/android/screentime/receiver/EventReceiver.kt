package de.markusfisch.android.screentime.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.markusfisch.android.screentime.service.startTrackerService

const val ACTION = "action"
const val TIMESTAMP = "timestamp"

class EventReceiver : BroadcastReceiver() {
	override fun onReceive(context: Context, intent: Intent?) {
		intent?.action?.let {
			context.sendIntent(it)
		}
	}
}

private fun Context.sendIntent(action: String) {
	startTrackerService { intent ->
		intent.apply {
			putExtra(ACTION, action)
			putExtra(TIMESTAMP, System.currentTimeMillis())
		}
	}
}
