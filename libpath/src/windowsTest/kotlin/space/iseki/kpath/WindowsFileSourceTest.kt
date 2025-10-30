package space.iseki.kpath

import kotlinx.io.buffered
import kotlinx.io.readString
import kotlin.test.Test
import kotlin.test.assertEquals

class WindowsFileSourceTest {
    @Test
    fun testRead() {
        val path = Path("""src\windowsTest\resources\test_read""")
        val s = PlatformFileSystem.openRead(path).use { it.buffered().readString() }
        assertEquals("0d000721", s)
    }

    @Test
    fun testSeekFromMiddle() {
        val path = Path("""src\windowsTest\resources\test_read""")
        PlatformFileSystem.openRead(path).use { src ->
            src.seek(2)
            val s = src.buffered().readString()
            assertEquals("000721", s)
        }
    }

}
