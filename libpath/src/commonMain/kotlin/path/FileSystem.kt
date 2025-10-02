package path

interface FileSystem {
    val separator: String
    val roots: List<Path>

    interface DirEntry {
        val name: String
    }

    interface DirEntryIterator : Iterator<DirEntry>, AutoCloseable
}

expect val PlatformFileSystem: FileSystem

