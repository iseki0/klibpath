package space.iseki.kpath

import kotlin.test.Test
import kotlin.test.assertEquals

class ExceptionsTest {

    @Test
    fun invalidPathExceptionTest() {
        val a = InvalidPathException("path", "reason")
        assertEquals(a.message, "reason: path")
    }
}
