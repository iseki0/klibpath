package space.iseki.kpath

class FileSystemTest {

    //    @Test
    fun testWalkCodeDir() {
        println(Path(".").toAbsolute())
        PlatformFileSystem.walk(Path(".")) {
            visitDirectory = true
        }.use { walker ->
            walker.forEach { path -> println(path) }
        }
    }
}