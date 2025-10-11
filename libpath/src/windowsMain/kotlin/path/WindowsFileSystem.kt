@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

package path

import kotlinx.cinterop.Arena
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.windows.DeleteFileW
import platform.windows.ERROR_FILE_NOT_FOUND
import platform.windows.ERROR_NO_MORE_FILES
import platform.windows.FALSE
import platform.windows.FILE_ATTRIBUTE_DIRECTORY
import platform.windows.FindClose
import platform.windows.FindFirstFileW
import platform.windows.FindNextFileW
import platform.windows.GetLastError
import platform.windows.GetLogicalDrives
import platform.windows.HANDLE
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.WIN32_FIND_DATAW
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.Cleaner
import kotlin.native.ref.createCleaner

internal data object WindowsFileSystem : FileSystem {
    override val separator: String get() = "\\"
    override val roots: List<Path>
        get() = run {
            var mask = GetLogicalDrives()
            if (mask == 0u) throw translateIOError()
            List(mask.countOneBits()) {
                val ch = 'A' + mask.takeLowestOneBit().countTrailingZeroBits()
                mask = mask and mask.takeLowestOneBit().inv()
                WindowsPath.of("""$ch:\""")
            }
        }

    override fun openDirectoryIterator(path: Path): FileSystem.DirEntryIterator {
        return WindowsDirIterator(path.win.value)
    }

    override fun delete(path: Path, ignoreIfNotExists: Boolean) {
        val p = path.win.value
        if (DeleteFileW(p) == FALSE) {
            try {
                translateIOError(file = p)
            } catch (e: NoSuchFileException) {
                if (!ignoreIfNotExists) throw e
            }
        }
    }

    private val Path.win: WindowsPath
        get() = this as? WindowsPath ?: throw IllegalArgumentException("Only WindowsPath is supported: $this")
}


internal class WindowsDirIterator : FileSystem.DirEntryIterator, AbstractIterator<FileSystem.DirEntry> {
    private class H(val m: Arena, val handle: HANDLE?, val p: CPointer<WIN32_FIND_DATAW>) : OneTimeClose() {
        override fun doClose() {
            if (handle != INVALID_HANDLE_VALUE) {
                FindClose(handle)
            }
            m.clear()
        }
    }

    data class Entry(override val name: String, override val isDirectory: Boolean) : FileSystem.DirEntry


    private val h: H
    private val dir: String
    private val cleaner: Cleaner


    constructor(path: String) {
        dir = if (path.endsWith('\\')) "$path*" else "$path\\*"
        val m = Arena()
        try {
            val p = m.alloc<WIN32_FIND_DATAW>()
            val handle = FindFirstFileW(dir, p.ptr)
            h = H(m = m, handle = handle, p = p.ptr)
            cleaner = createCleaner(h, H::close)
            if (handle == INVALID_HANDLE_VALUE) {
                val code = GetLastError()
                if (code == ERROR_FILE_NOT_FOUND.toUInt()) {
                    done()
                } else {
                    throw translateIOError(file = path, code = code)
                }
            } else {
                tryCommitEntry() // first entry is already read
            }
        } catch (th: Throwable) {
            m.clear()
            throw th
        }

    }

    private fun tryCommitEntry(): Boolean {
        val entryName = h.p.pointed.cFileName.toKString()
        if (entryName == "." || entryName == "..") {
            return false
        }
        val entry = Entry(
            name = entryName,
            isDirectory = h.p.pointed.dwFileAttributes and FILE_ATTRIBUTE_DIRECTORY.toUInt() != 0u,
        )
        setNext(entry)
        return true
    }

    override fun close() {
        h.close()
    }

    override tailrec fun computeNext() {
        check(!h.isClosed) { "Iterator is closed" }
        if (FindNextFileW(hFindFile = h.handle, lpFindFileData = h.p) == 0) {
            val code = GetLastError()
            if (code == ERROR_NO_MORE_FILES.toUInt()) {
                done()
                return
            }
            throw translateIOError(file = dir)
        }
        if (tryCommitEntry()) return else computeNext()
    }

}

