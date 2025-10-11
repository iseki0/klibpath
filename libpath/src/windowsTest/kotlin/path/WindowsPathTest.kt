package path

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WindowsPathTest {
    @Test
    fun testIsAbsoluteAndRoot() {
        val root = WindowsPath.of("C:\\")
        assertTrue(root.isAbsolute, "Expected absolute: C:\\")
        assertNull(root.parent, "Root should have no parent (C:\\)")

        val rel = WindowsPath.of("foo\\bar")
        assertFalse(rel.isAbsolute, "Expected relative: foo\\bar")
    }

    @Test
    fun testFilenameExtraction() {
        val p1 = WindowsPath.of("C:\\Users\\test.txt")
        assertEquals("test.txt", p1.filename, "Filename mismatch for C:\\Users\\test.txt")

        val p2 = WindowsPath.of("C:\\")
        assertNull(p2.filename, "Root should have null filename (C:\\)")
    }

    @Test
    fun testJoinPath() {
        val base = WindowsPath.of("C:\\Users")
        val joined = base.join("test", "docs")
        assertEquals(
            "C:\\Users\\test\\docs", joined.toString(),
            "Join mismatch for base=C:\\Users with [test, docs]",
        )

        val withEmpty = base.join("", "foo")
        assertEquals(
            "C:\\Users\\foo", withEmpty.toString(),
            "Join mismatch for base=C:\\Users with ['', foo]",
        )
    }

    @Test
    fun testCanonicalize() {
        val p = WindowsPath.of("C:\\Users\\\\test\\..\\test2\\")
        assertTrue(p.toString().endsWith("test2"), "Canonicalize mismatch: $p")
    }

    @Test
    fun testToAbsolute() {
        val rel = WindowsPath.of("folder\\file.txt")
        val abs = rel.toAbsolute()
        assertTrue(abs.isAbsolute, "Expected absolute path after toAbsolute(): $abs")
        assertEquals(abs, abs.toAbsolute(), "toAbsolute() should be idempotent: $abs")
    }

    @Test
    fun testParentResolution() {
        val p = WindowsPath.of("C:\\Users\\test.txt")
        assertEquals(
            "C:\\Users", p.parent.toString(),
            "Parent mismatch for C:\\Users\\test.txt",
        )

        val q = WindowsPath.of("C:\\Users\\")
        assertEquals(
            "C:\\", q.parent.toString(),
            "Parent mismatch for C:\\Users\\",
        )
    }

    @Test
    fun testEqualityAndHashCode() {
        val p1 = WindowsPath.of("C:\\Users\\test")
        val p2 = WindowsPath.of("C:\\Users\\test")
        assertEquals(p1, p2, "Equality failed for same path C:\\Users\\test")
        assertEquals(
            p1.hashCode(), p2.hashCode(),
            "HashCode mismatch for C:\\Users\\test",
        )
    }

    @Test
    fun testEvalSymlink() {
        val p = WindowsPath.of("C:\\Users\\All Users").evalSymlink()
        assertEquals(WindowsPath.of("C:\\ProgramData"), p)
        println(p.toString())
    }

    @Test
    fun testEvalSymlinkNotExists() {
        assertFailsWith<NoSuchFileException> {
            WindowsPath.of("C:\\Users\\All Users11111").evalSymlink()
        }
    }



}

