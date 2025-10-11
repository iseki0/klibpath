package path

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WindowsPathUtilTest {
    @Test
    fun testNormalization() {
        fun assertNormalized(expected: String, input: String) {
            val n = WindowsPathUtil.normalizePath(input)
            assertEquals(expected, n, "input: $input")
        }

        fun assertNormalized1(expected: String, input: String) {
            val n = WindowsPathUtil.normalizePath(input)
            assertEquals(expected, n, "input: $input")
            val input1 = input.replace('\\', '/')
            val n1 = WindowsPathUtil.normalizePath(input1)
            assertEquals(expected, n1, "input: $input1")
        }

        assertNormalized("""\\server\share\""", """\\server\share""")
        assertNormalized("""\\server\share\""", """\\server\share\..\..\..""")
        assertNormalized("""\\server\share\a\c""", """\\server\share\a\b\..\c""")
        assertNormalized("""\\server\share\a\c""", """\\server\share\a\b\..\.\c""")
        assertNormalized("""\\server\share\a\c""", """\\server\share\a\\.\bbbbbb\..\c\""")

        assertNormalized("""\\?\C:\a\c""", """\\?\C:\a\b\..\c""")
        assertNormalized("""\\?\C:\a\c""", """\\?\C:\a\b\..\.\c""")
        assertNormalized("""\\?\C:\a\c""", """\\?\C:\a\\.\bbbbbb\..\c\""")

        assertNormalized("""\\?\C:\""", """\\?\C:\""")


        assertNormalized1("""a""", """a\\""")
        assertNormalized1("""a""", """a\\.""")
        assertNormalized1("""a""", """a\\\\.""")
        assertNormalized1("""a""", """a\.""")
        assertNormalized1("""a""", """a\.\.\.\.\""")

        assertNormalized1("""..""", """a\..\..""")
        assertNormalized1("""..""", """a\..\..\.""")
        assertNormalized1("""..""", """aaa\.\..\..\\""")

        assertNormalized1("""\a""", """\a\.\.\.\.""")
        assertNormalized1("""\a""", """\a\.\.\.\.\""")

        assertNormalized1("""\""", """\a\..""")
        assertNormalized1("""\""", """\a\..\..""")
        assertNormalized1("""\""", """\a\..\..\""")
        assertNormalized1("""\""", """\a\..\..\.""")
        assertNormalized1("""\""", """\a\.\..""")


        assertNormalized1("""C:\""", """c:""")

        assertNormalized1("""C:\a""", """c:\a""")

        assertNormalized1("""C:\a""", """C:\a""")
        assertNormalized1("""C:\a""", """C:\a\""")
        assertNormalized1("""C:\a""", """C:\a\.\.\.\.""")

        assertNormalized1("""C:\""", """C:\a\..""")
        assertNormalized1("""C:\""", """C:\a\..\""")
        assertNormalized1("""C:\""", """C:\a\..\..\""")
        assertNormalized1("""C:\""", """C:\a\..\..\.\""")
        assertNormalized1("""C:\""", """C:\a\.\..\..\""")


        assertNormalized1("""C:\a\c""", """C:\a\b\..\c""")
        assertNormalized1("""C:\a\c""", """C:\a\b\..\.\c""")
        assertNormalized1("""C:\a\c""", """C:\a\\.\bbbbbb\..\c\""")
    }

    @Test
    fun testNormalizationFailure() {
        assertFailsWith<InvalidPathException> {
            WindowsPathUtil.normalizePath("""\\""") // UNC 但缺少 server/share
        }
        assertFailsWith<InvalidPathException> {
            WindowsPathUtil.normalizePath("""\\?\UNC\""") // 长 UNC 但不完整
        }
        assertFailsWith<InvalidPathException> {
            WindowsPathUtil.normalizePath("""\\?\""") // 长路径前缀后为空
        }
        assertFailsWith<InvalidPathException> {
            WindowsPathUtil.normalizePath("C:<>file") // 含非法字符
        }
    }


    private fun assertPathProp(
        input: String,
        isLong: Boolean,
        isUNC: Boolean,
        hasDosPrefix: Boolean,
        isAbs: Boolean,
        isNoPath: Boolean,
        expectedStart: Int
    ) {
        val p = WindowsPathUtil.PathProp.analyze(input)
        assertEquals(isLong, p.isLong, "isLong mismatch for '$input'")
        assertEquals(isUNC, p.isUNC, "isUNC mismatch for '$input'")
        assertEquals(hasDosPrefix, p.hasDosPrefix, "hasDosPrefix mismatch for '$input'")
        assertEquals(isAbs, p.isAbs, "isAbs mismatch for '$input'")
        assertEquals(isNoPath, p.isNoPath, "isNoPath mismatch for '$input'")
        assertEquals(expectedStart, p.start, "start mismatch for '$input'")
    }

    @Test
    fun testDriveAbsolute() {
        assertPathProp(
            input = "C:\\Windows",
            isLong = false,
            isUNC = false,
            hasDosPrefix = true,
            isAbs = true,
            isNoPath = false,
            expectedStart = 2,
        )
    }

    @Test
    fun testDriveRelative() {
        assertPathProp(
            input = "C:folder",
            isLong = false,
            isUNC = false,
            hasDosPrefix = true,
            isAbs = false,
            isNoPath = false,
            expectedStart = 2,
        )
    }

    @Test
    fun testUncPath() {
        assertPathProp(
            input = """\\server\share\foo""",
            isLong = false,
            isUNC = true,
            hasDosPrefix = false,
            isAbs = true,
            isNoPath = false,
            expectedStart = """\\server\share""".length,
        )
    }

    @Test
    fun testLongUncPath() {
        assertPathProp(
            input = """\\?\UNC\server\share\foo""",
            isLong = true,
            isUNC = true,
            hasDosPrefix = false,
            isAbs = true,
            isNoPath = false,
            expectedStart = """\\?\UNC\server\share""".length,
        )
    }

    @Test
    fun testLongAbsoluteDrive() {
        assertPathProp(
            input = """\\?\C:\Program Files""",
            isLong = true,
            isUNC = false,
            hasDosPrefix = true,
            isAbs = true,
            isNoPath = false,
            expectedStart = 6, // \\?\ + C:
        )
    }

    @Test
    fun testRelativePath() {
        assertPathProp(
            input = "foo\\bar",
            isLong = false,
            isUNC = false,
            hasDosPrefix = false,
            isAbs = false,
            isNoPath = false,
            expectedStart = 0,
        )
    }

    @Test
    fun testEmptyPath() {
        assertPathProp(
            input = "C:",
            isLong = false,
            isUNC = false,
            hasDosPrefix = true,
            isAbs = false,
            isNoPath = true,
            expectedStart = 2,
        )
    }


    @Test
    fun testJoinPath() {
        fun assertJoined(expected: String, first: String, second: String) {
            val j = WindowsPathUtil.joinPath(first, second)
            assertEquals(expected, j, "join('$first', '$second')")
        }

        assertJoined("""C:\a\b""", """C:\a""", """b""")
        assertJoined("""C:\a\b""", """C:\a\""", """b""")
        assertJoined("""C:\a\.\.\.\.\b""", """C:\a\.\.\.\.""", """b""")
    }

    @Test
    fun testJoinPath_Comprehensive() {
        fun assertJoined(expected: String, first: String, second: String) {
            val j = WindowsPathUtil.joinPath(first, second)
            assertEquals(expected, j, "join('$first', '$second')")
        }

        // 你给的示例（保留）
        assertJoined("""C:\a\b""", """C:\a""", """b""")
        assertJoined("""C:\a\b""", """C:\a\""", """b""")
        assertJoined("""C:\a\.\.\.\.\b""", """C:\a\.\.\.\.""", """b""")

        // 1) 最常见：相对段拼接（不折叠 . / ..）
        assertJoined("""C:\a\b\c""", """C:\a\b""", """c""")
        assertJoined("""C:\a\b\.""", """C:\a\b""", """.""")
        assertJoined("""C:\a\b\..\c""", """C:\a\b""", """..\c""")
        // 可越根：保留 ..，不报错、不折叠
        assertJoined("""C:\..\..\x""", """C:\""", """..\..\x""")

//        // 2) 第二参数为根化路径（以反斜杠开头）：挂到 first 的盘符根
//        // 例如 "\x" 在 Windows 语义是“当前盘”的根。这里用 first 的盘符。
//        assertJoined("""C:\x""", """C:\a\b""", """\x""")
//        // UNC 情况：first 是 UNC，second 根化到该 UNC 的根
//        assertJoined("""\\server\share\x""", """\\server\share\base""", """\x""")

        // 3) 第二参数为盘符根化路径（绝对路径）：直接返回 second
        assertJoined("""D:\x""", """C:\a\b""", """D:\x""")
        assertJoined("""C:\root""", """C:\a\b""", """C:\root""") // 覆盖自身盘

//        // 4) 第二参数为“盘符相对路径”（带盘符但不以 '\' 开头，如 C:foo）
//        // 规则建议：
//        //  - 若盘符与 first 相同：在 first 上继续追加（等价“同盘相对”）
//        //  - 若盘符不同：无法基于 first 继续，相当于“以该盘当前目录”为基，保留原样
//        assertJoined("""C:\a\b\rel\y""", """C:\a\b""", """C:rel\y""") // 同盘
//        assertJoined("""D:rel\y""", """C:\a\b""", """D:rel\y""")       // 异盘

        // 5) UNC 路径
        assertJoined("""\\server\share\base\x""", """\\server\share\base""", """x""")
        // 若 second 自身为 UNC 绝对路径：直接返回 second
        assertJoined("""\\other\sh\r\dst""", """\\server\share\base""", """\\other\sh\r\dst""")

        // 6) 设备路径（Win32 “原始”前缀）
        // 若 first 为设备路径：正常追加
        assertJoined("""\\?\C:\base\x""", """\\?\C:\base""", """x""")
        assertJoined("""\\?\UNC\server\share\dir\y""", """\\?\UNC\server\share\dir""", """y""")
        // 若 second 为设备路径绝对：直接返回 second
        assertJoined("""\\?\C:\foo\bar""", """C:\a\b""", """\\?\C:\foo\bar""")
        assertJoined("""\\?\UNC\srv\shr\z""", """C:\a""", """\\?\UNC\srv\shr\z""")

        // 7) first 为根（允许根有末尾反斜杠）
        assertJoined("""C:\b""", """C:\""", """b""")
        assertJoined("""\\server\share\b""", """\\server\share""", """b""")

        // 8) first 为相对路径
        assertJoined("""a\b\c""", """a\b""", """c""")
        // 若 second 为根化（以 '\' 开头）：保持为根化路径，不附加盘符（按 Windows 语义）
        assertJoined("""\rooted""", """a\b""", """\rooted""")

        // 9) 空串边界（如果你实现里支持）
        // - second 为空：返回 first 原样
        assertJoined("""C:\a\b""", """C:\a\b""", """""")
        // - first 为空：返回 second 原样
        assertJoined("""rel\p""", """""", """rel\p""")
        assertJoined("""\abs""", """""", """\abs""")
        assertJoined("""D:\abs""", """""", """D:\abs""")

        // 10) 多段与特殊点位（不折叠）
        assertJoined("""C:\a\.\b\..\c\.\d""", """C:\a\.\b""", """..\c\.\d""")
        assertJoined("""\\server\share\.\..\x""", """\\server\share\.""", """..\x""")
    }


}
