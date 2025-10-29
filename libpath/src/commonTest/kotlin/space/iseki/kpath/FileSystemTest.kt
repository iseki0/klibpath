package space.iseki.kpath

import kotlin.test.Test

class FileSystemTest {

    @Test
    fun testWalkCodeDir() {
        println(Path(".").toAbsolute())
        PlatformFileSystem.walk(Path(".")) {
            visitDirectory = true
        }.use { walker ->
            walker.forEach { path -> println(path) }
        }
    }
}