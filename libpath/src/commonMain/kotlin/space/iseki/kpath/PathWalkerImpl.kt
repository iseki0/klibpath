package space.iseki.kpath

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

    class WalkNode(val dir: Path) : AutoCloseable {
        val iterator = dir.fileSystem.openDirectoryIterator(dir)
        val fileKey = try {
            dir.fileSystem.getFileKey(dir)
        } catch (_: Exception) {
            null
        }

        override fun close() {
            iterator.close()
        }
    }

    private val stack: ArrayDeque<WalkNode> = ArrayDeque(listOf(WalkNode(start)))
    private var closed = false
    override fun close() {
        closed = true
        stack.forEach(WalkNode::close)
    }

    private fun isSame(node: WalkNode, childPath: Path): Boolean {
        val nodeKey = node.fileKey
        if (nodeKey != null) {
            val childKey = try {
                childPath.fileSystem.getFileKey(childPath)
            } catch (_: Exception) {
                null
            }
            if (childKey != null) {
                return childKey == node.fileKey
            }
        }
        return try {
            childPath.fileSystem.isSameFile(childPath, node.dir)
        } catch (_: Exception) {
            true
        }
    }

    override tailrec fun computeNext() {
        check(!closed)
        val node = stack.lastOrNull() ?: run {
            done()
            return
        }
        if (!node.iterator.hasNext()) {
            node.close()
            stack.removeLast()
            option.onLeave(node.dir)
            computeNext()
            return
        }
        val entry = node.iterator.next()
        val childPath = node.dir.join(entry.name)
        if (!entry.isDirectory) {
            setNext(childPath)
            return
        }
        if (stack.any { node -> isSame(node, childPath) }) {
            computeNext()
            return
        }
        if (option.onEnter(childPath)) {
            stack.add(WalkNode(childPath))
        }
        if (option.visitDirectory) {
            setNext(childPath)
            return
        }
        computeNext()
    }

}
