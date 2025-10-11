package path

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
}

expect val PlatformFileSystem: FileSystem

internal class PathWalkerImpl(start: Path, configure: FileSystem.WalkOption.() -> Unit) : FileSystem.Walker,
    AbstractIterator<Path>() {
    private val option = object : FileSystem.WalkOption {
        var onEnter: (Path) -> Boolean = { true }
        var onLeave: (Path) -> Unit = {}
        override fun onEnter(action: (Path) -> Boolean) {
            onEnter = action
        }

        override fun onLeave(action: (Path) -> Unit) {
            onLeave = action
        }

        override var visitDirectory: Boolean = true
    }.apply { configure() }

    private val stack: ArrayDeque<Pair<Path, FileSystem.DirEntryIterator>> =
        ArrayDeque(listOf(start to start.fileSystem.openDirectoryIterator(start)))
    private var closed = false
    override fun close() {
        closed = true
        stack.forEach { (_, i) -> i.close() }
    }

    override tailrec fun computeNext() {
        check(!closed)
        val (currentDirectory, iterator) = stack.lastOrNull() ?: run {
            done()
            return
        }
        if (!iterator.hasNext()) {
            iterator.close()
            stack.removeLast()
            option.onLeave(currentDirectory)
            computeNext()
            return
        }
        val entry = iterator.next()
        val childPath = currentDirectory.join(entry.name)
        if (!entry.isDirectory) {
            setNext(childPath)
            return
        }
        if (stack.any { (path) -> childPath.fileSystem.isSameFile(childPath, path) }) {
            computeNext()
            return
        }
        if (option.onEnter(childPath)) {
            stack.add(childPath to childPath.fileSystem.openDirectoryIterator(childPath))
        }
        if (option.visitDirectory) {
            setNext(childPath)
            return
        }
        computeNext()
    }

}