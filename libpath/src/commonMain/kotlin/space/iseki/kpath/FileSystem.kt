package space.iseki.kpath

import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlin.jvm.JvmStatic

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

    fun evalSymlink(path: Path): Path

    fun isSameFile(path1: Path, path2: Path): Boolean {
        return evalSymlink(path1).toAbsolute() == evalSymlink(path2).toAbsolute()
    }

    fun openRead(path: Path): RawSource

    fun getFileKey(path: Path): Any? = null

    fun openWrite(path: Path): RawSink

    fun openWrite(path: Path, create: Boolean = true, createNew: Boolean = false, truncate: Boolean = false): RawSink

    fun mkdir(path: Path)

    fun mkdirs(path: Path)

    companion object {
        @JvmStatic
        fun platform(): FileSystem = PlatformFileSystem
    }
}

internal expect val PlatformFileSystem: FileSystem
