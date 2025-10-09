package path

interface FileSystem {
    val separator: String
    val roots: List<Path>

    interface DirEntry {
        val name: String
    }

    interface DirEntryIterator : Iterator<DirEntry>, AutoCloseable

    fun openDirectoryIterator(path: Path): DirEntryIterator

    fun listDirectoryEntries(path: Path): List<DirEntry> {
        openDirectoryIterator(path).use { iter ->
            return iter.asSequence().toList()
        }
    }
}

expect val PlatformFileSystem: FileSystem

