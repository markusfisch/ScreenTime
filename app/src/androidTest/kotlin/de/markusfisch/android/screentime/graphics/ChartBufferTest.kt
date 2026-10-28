package de.markusfisch.android.screentime.graphics

import android.graphics.Color

fun checkChartBuffers() {
	val paint = fillPaint(Color.WHITE)
	val now = System.currentTimeMillis()
	val clock = UsageChart(128, 128, paint, paint, paint)
	val first = clock.draw(now, 0, "Today")
	val before = first.copy(first.config!!, false)
	clock.swapBuffers()
	val discarded = clock.draw(now, 1, "Discarded")
	val latest = clock.draw(now, 2, "Latest")
	check(discarded === latest)
	check(first !== latest)
	check(first.sameAs(before))
	before.recycle()

	val multi = MultiDayChart(128, 1f, 2, paint, paint, paint, paint)
	val firstDays = multi.draw(now)
	val beforeDays = firstDays.copy(firstDays.config!!, false)
	multi.swapBuffers()
	val discardedDays = multi.draw(now - 86400000L)
	val latestDays = multi.draw(now - 172800000L)
	check(discardedDays === latestDays)
	check(firstDays !== latestDays)
	check(firstDays.sameAs(beforeDays))
	beforeDays.recycle()
}
