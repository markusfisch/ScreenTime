package de.markusfisch.android.screentime.preference

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import de.markusfisch.android.screentime.app.prefs
import de.markusfisch.android.screentime.database.startOfDay
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min

class Preferences(context: Context) {
	private val preferences: SharedPreferences =
		PreferenceManager.getDefaultSharedPreferences(context)
	private val minDurationLengthenChoice = intArrayOf(
		0, 1000, 5000, 10000, 15000, 20000, 30000, 40000,
		50000, 60000, 80000, 100000, 120000, 150000, 3 * 60000, 4 * 60000,
		5 * 60000, 6 * 60000, 7 * 60000, 8 * 60000, 9 * 60000, 10 * 60000, 12 * 60000, 15 * 60000,
		20 * 60000, 30 * 60000, 40 * 60000, 50 * 60000, 60 * 60000
	)

	var graphRange = 0
		set(value) {
			apply(GRAPH_RANGE, value)
			field = value
		}
	var hourOfDayChange = 0
		set(value) {
			val clamped = min(23, max(0, value))
			apply(HOUR_OF_DAY_CHANGE, clamped)
			field = clamped
		}
	var minDurationLengthen = 0
		set(value) {
			apply(MIN_DURATION_LENGTHEN, value)
			field = value
		}

	fun minDurationLengthenValue() = minDurationLengthenChoice.getOrNull(minDurationLengthen) ?: 0

	init {
		graphRange = preferences.getInt(GRAPH_RANGE, graphRange)
		hourOfDayChange = preferences.getInt(
			HOUR_OF_DAY_CHANGE,
			hourOfDayChange
		)
		minDurationLengthen = preferences.getInt(MIN_DURATION_LENGTHEN, minDurationLengthen)
	}

	fun dayStart(timestamp: Long): Long = startOfDay(timestamp) { cal ->
		cal.set(Calendar.HOUR_OF_DAY, prefs.hourOfDayChange)
		if (cal.timeInMillis > timestamp) {
			cal.add(Calendar.DAY_OF_MONTH, -1)
		}
	}

	private fun apply(label: String, value: Int) {
		preferences.edit().putInt(label, value).apply()
	}

	companion object {
		private const val GRAPH_RANGE = "graph_range"
		private const val HOUR_OF_DAY_CHANGE = "hour_of_day_change"
		private const val MIN_DURATION_LENGTHEN = "min_duration_lengthen"
	}
}
