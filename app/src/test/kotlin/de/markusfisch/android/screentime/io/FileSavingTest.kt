package de.markusfisch.android.screentime.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class FileSavingTest {
	@get:Rule val directory = TemporaryFolder()

	@Test fun failedWriteRemovesPartialFile() {
		val file = File(directory.root, "export.db")
		assertThrows(IOException::class.java) {
			writeFile(file) {
				it.write(byteArrayOf(1, 2, 3))
				throw IOException("Injected write failure")
			}
		}
		assertFalse(file.exists())
	}

	@Test fun existingFileIsNeverModifiedOrDeleted() {
		val file = directory.newFile("existing.db")
		file.writeText("original")
		assertFalse(writeFile(file) { error("Must not open an existing file") })
		assertEquals("original", file.readText())
	}

	@Test fun successfulWriteKeepsCompleteFile() {
		val file = File(directory.root, "export.db")
		assertTrue(writeFile(file) { it.write("complete".toByteArray()) })
		assertEquals("complete", file.readText())
	}
}
