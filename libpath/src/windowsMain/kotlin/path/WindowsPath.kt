@file:OptIn(ExperimentalForeignApi::class)

package path

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.windows.BY_HANDLE_FILE_INFORMATION
import platform.windows.CloseHandle
import platform.windows.CreateFileW
import platform.windows.FALSE
import platform.windows.FILE_FLAG_BACKUP_SEMANTICS
import platform.windows.FILE_READ_ATTRIBUTES
import platform.windows.FILE_SHARE_DELETE
import platform.windows.FILE_SHARE_READ
import platform.windows.FILE_SHARE_WRITE
import platform.windows.GetFileInformationByHandle
import platform.windows.GetFinalPathNameByHandleW
import platform.windows.GetFullPathNameW
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.MAX_PATH
import platform.windows.OPEN_EXISTING
import platform.windows.PathIsRelativeW
import platform.windows.TRUE
import platform.windows.WCHARVar

internal class WindowsPath : Path {
    internal val value: String
    val filenameIdx: Int

    private constructor(value: String) {
        this.value = value
        filenameIdx = WindowsPathUtil.getFilenameIndex(value)
    }

    companion object {
        internal fun ofNormalized(path: String) = WindowsPath(path)
        internal fun of(path: String) = ofNormalized(WindowsPathUtil.normalizePath(path, collapse = false))
    }

    override val fileSystem: FileSystem
        get() = WindowsFileSystem

    override val isAbsolute: Boolean get() = PathIsRelativeW(value) != TRUE

    override val parent: Path?
        get() = if (filenameIdx == -1) null else of(value.substring(0, filenameIdx))

    override val filename: String?
        get() = if (filenameIdx != -1) value.substring(filenameIdx) else null


    override fun join(vararg other: String): Path = other.fold(value) { base, other ->
        WindowsPathUtil.joinPath(base, WindowsPathUtil.normalizePath(other, collapse = false))
    }.let(::of)

    override fun normalization(): Path = ofNormalized(WindowsPathUtil.normalizePath(value))

    override fun toAbsolute(): Path = ofNormalized(getFullPath(value))

    override fun evalSymlink(): Path = of(evalSymlink(value))

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as WindowsPath

        return value == other.value
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }

    override fun toString(): String {
        return value
    }
}


private fun getFullPath(s: String): String {
    memScoped {
        var buf = allocArray<WCHARVar>(MAX_PATH)
        var n = GetFullPathNameW(s, MAX_PATH.convert(), buf, null)
        if (n >= MAX_PATH.convert()) {
            buf = allocArray<WCHARVar>(n.convert())
            n = GetFullPathNameW(s, n, buf, null)
        }
        if (n == 0u) {
            throw InvalidPathException(s, formatErrorCode())
        }
        return buf.toKString()
    }
}


private fun evalSymlink(s: String): String {
    memScoped {
        val h = CreateFileW(
            s, FILE_READ_ATTRIBUTES.toUInt(), (FILE_SHARE_READ or FILE_SHARE_WRITE or FILE_SHARE_DELETE).toUInt(),
            lpSecurityAttributes = null,
            dwCreationDisposition = OPEN_EXISTING.toUInt(),
            dwFlagsAndAttributes = FILE_FLAG_BACKUP_SEMANTICS.toUInt(),
            hTemplateFile = null,
        )
        if (h == INVALID_HANDLE_VALUE) {
            throw translateIOError(file = s)
        }
        try {
            var buf = allocArray<WCHARVar>(MAX_PATH)
            var n = GetFinalPathNameByHandleW(h, buf, MAX_PATH.convert(), 0u)
            if (n >= MAX_PATH.convert()) {
                buf = allocArray<WCHARVar>(n.convert())
                n = GetFinalPathNameByHandleW(h, buf, n, 0u)
            }
            if (n == 0u) {
                error("GetFinalPathNameByHandleW failed: ${formatErrorCode()}")
            }
            return buf.toKString()
        } finally {
            CloseHandle(h)
        }
    }
}

