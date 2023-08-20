package de.markusfisch.android.screentime.database

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import de.markusfisch.android.screentime.R
import de.markusfisch.android.screentime.app.db
import java.io.InputStream
import java.io.OutputStream

fun Context.importDatabase(uri: Uri?): String {
	val cantFindDb: String = getString(R.string.cant_find_db)
	if (uri == null) {
		return cantFindDb
	}
	val cr: ContentResolver = contentResolver ?: return cantFindDb
	val fileName = "import.db"
	var inputStream: InputStream? = null
	var outputStream: OutputStream? = null
	try {
		inputStream = cr.openInputStream(uri)
		if (inputStream == null) {
			return cantFindDb
		}
		outputStream = openFileOutput(fileName, Context.MODE_PRIVATE)
		val buffer = ByteArray(4096)
		var len: Int
		while ((inputStream.read(buffer).also { len = it }) != -1) {
			outputStream.write(buffer, 0, len)
		}
	} catch (e: java.io.IOException) {
		return getString(R.string.import_failed, e.message)
	} finally {
		try {
			inputStream?.close()
			outputStream?.close()
		} catch (e: java.io.IOException) {
			// Ignore, can't do anything about it.
		}
	}
	val message = db.importDatabase(
		this, fileName
	) ?: getString(R.string.import_successful)
	deleteFile(fileName)
	return message
}
