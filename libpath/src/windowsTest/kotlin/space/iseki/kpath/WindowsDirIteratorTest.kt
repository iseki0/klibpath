@file:OptIn(ExperimentalForeignApi::class)

package space.iseki.kpath

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import platform.windows.CreateDirectoryW
import platform.windows.GetTempPathW
import platform.windows.MAX_PATH
import platform.windows.RemoveDirectoryW
import platform.windows.WCHARVar
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WindowsDirIteratorTest {
    @Test
    fun test() {
        val l = WindowsDirIterator("src/windowsTest").use { iter -> iter.asSequence().map { it.name }.toList() }
        assertEquals(listOf("kotlin", "resources"), l)
    }

    @Test
    fun testDirNoEntry() {
        assertFailsWith<NoSuchFileException> {
            WindowsDirIterator("src/windowsTest111")
        }
    }

    @Test
    fun testEmptyDir() {
        memScoped {
            val path = allocArray<WCHARVar>(MAX_PATH)
            check(GetTempPathW(MAX_PATH.toUInt(), path).convert() in 1..MAX_PATH)
            val tp = path.toKString().trimEnd()
            val dir = "$tp\\testdir-${Random.nextInt()}"
            println(dir)
            check(CreateDirectoryW(dir, null) != 0) { error(translateIOError(file = dir)) }
            try {
                assertEquals(emptyList(), WindowsDirIterator(dir).asSequence().toList())
            } finally {
                RemoveDirectoryW(dir)
            }
        }
    }

    @Test
    fun testNotDirectory() {
        assertFailsWith<NotDirectoryException> {
            WindowsDirIterator("./build.gradle.kts")
        }
    }

    private fun assertCDrive(path: String) {
        val entries = WindowsDirIterator(path).use { it.asSequence().toList() }
        entries.forEach { println(it) }
        assertTrue { entries.any { it.name == "Windows" && it.isDirectory } }
        assertTrue { entries.any { it.name == "Program Files" && it.isDirectory } }
    }

    @Test
    fun testReadDosRoot() {
        assertCDrive("C:\\")
    }

    @Test
    fun testReadDosRootLongPath() {
        assertCDrive("\\\\?\\C:\\")
    }
}