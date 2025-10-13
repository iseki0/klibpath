package path

import kotlinx.io.buffered
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlin.test.Test
import kotlin.test.assertEquals

class WindowsFileSinkTest {
    @Test
    fun testWrite() {
        val path = Path("""src\windowsTest\resources\test_write""")
        val expected = "0d000721"
        PlatformFileSystem.openWrite(path).buffered().use { sink ->
            sink.writeString(expected)
        }
        val actual = PlatformFileSystem.openRead(path).use { it.buffered().readString() }
        assertEquals(expected, actual)
        PlatformFileSystem.delete(path)
    }
}
