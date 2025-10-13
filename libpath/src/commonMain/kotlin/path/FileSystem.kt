package path

import kotlinx.io.RawSink
import kotlinx.io.RawSource

interface FileSystem {
    val separator: String
    val roots: List<Path>

    interface DirEntry {
        val name: String
        val isDirectory: Boolean
    }

    interface DirEntryIterator : Iterator<DirEntry>, AutoCloseable

    fun openDirectoryIterator(path: Path): DirEntryIterator

    fun listDirectoryEntries(path: Path): List<DirEntry> {
        openDirectoryIterator(path).use { iter ->
            return iter.asSequence().toList()
        }
    }

    fun delete(path: Path, ignoreIfNotExists: Boolean = false)

    interface Walker : Iterator<Path>, AutoCloseable

    fun walk(start: Path, configure: WalkOption.() -> Unit = {}): Walker = PathWalkerImpl(start, configure)

    interface WalkOption {
        fun onEnter(action: (Path) -> Boolean)
        fun onLeave(action: (Path) -> Unit)
        var visitDirectory: Boolean
    }

    fun isSameFile(path1: Path, path2: Path): Boolean {
        return path1.evalSymlink().toAbsolute() == path2.evalSymlink().toAbsolute()
    }

    fun openRead(path: Path): RawSource

    fun getFileKey(path: Path): Any? = null
    fun openWrite(path: Path): RawSink
}

expect val PlatformFileSystem: FileSystem
