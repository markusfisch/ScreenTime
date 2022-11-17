package de.markusfisch.android.screentime.data

import android.database.Cursor

// Overwrite Kotlin's ".use" function for Cursor because Cursor cannot
// be cast to Closeable below API level 16. This should be removed when
// the minSDK is increased to at least JELLY_BEAN (16).
inline fun <R> Cursor.use(block: (Cursor) -> R): R = try {
	block.invoke(this)
} finally {
	close()
}
