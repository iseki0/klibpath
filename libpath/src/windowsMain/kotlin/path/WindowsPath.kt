@file:OptIn(ExperimentalForeignApi::class)

package path

import kotlinx.cinterop.CArrayPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import kotlinx.cinterop.wcstr
import platform.windows.CloseHandle
import platform.windows.CreateFileW
import platform.windows.E_OUTOFMEMORY
import platform.windows.FILE_FLAG_BACKUP_SEMANTICS
import platform.windows.FILE_READ_ATTRIBUTES
import platform.windows.FILE_SHARE_DELETE
import platform.windows.FILE_SHARE_READ
import platform.windows.FILE_SHARE_WRITE
import platform.windows.GetFinalPathNameByHandleW
import platform.windows.GetFullPathNameW
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.LocalFree
import platform.windows.MAX_PATH
import platform.windows.OPEN_EXISTING
import platform.windows.PWSTRVar
import platform.windows.PathIsRelativeW
import platform.windows.S_OK
import platform.windows.TRUE
import platform.windows.WCHARVar
import kotlin.math.max

internal class WindowsPath : Path {
    private val value: String
    override val isRoot: Boolean

    private constructor(value: String) {
        this.value = value
        this.isRoot = memScoped { Syscall.PathCchIsRoot(value.wcstr.ptr) == TRUE }
    }

    companion object {
        internal fun ofCanonicalized(path: String) = WindowsPath(path)
        internal fun of(path: String) = ofCanonicalized(canonicalizePath(path))
    }

    override val fileSystem: FileSystem
        get() = WindowsFileSystem

    override val isAbsolute: Boolean get() = PathIsRelativeW(value) != TRUE

    override val parent: Path?
        get() = if (isRoot) null else of(removeFileSpec(value)).takeUnless { it == this }

    override val filename: String?
        get() = if (isRoot) null else value.substringAfterLast(separator)

    override fun join(vararg other: String): Path = WindowsPath(joinPath(value, *other))

    override fun canonicalize(): Path = WindowsPath(value)

    override fun toAbsolute(): Path = ofCanonicalized(getFullPath(value))

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

private fun joinPath(base: String, vararg other: String): String {
    memScoped {
        val bufSize = base.length + other.sumOf { it.length + 5 }
        val buf = allocArray<WCHARVar>(bufSize)
        appendPathWithSlashHandled(base, buf)
        var isFirst = true
        val otherBufSize = max(other.maxOf { it.length }, 1)
        val otherBuf = allocArray<WCHARVar>(otherBufSize)
        for (o in other) {
            if (!isFirst && o.isEmpty()) continue
            isFirst = false
            appendPathWithSlashHandled(o, otherBuf)
            val e = Syscall.PathCchCombineEx(
                p1 = buf,
                p2 = bufSize.convert(),
                p3 = buf,
                p4 = otherBuf,
                p5 = 1u,
            )
            when (e) {
                S_OK -> {}
                E_OUTOFMEMORY -> throw OutOfMemoryError("PathCchCombineEx failed")
                else -> throw InvalidPathException(o, formatErrorCode(e.toUInt()))
            }
        }
        return buf.toKString()
    }
}

private fun Char.isSeparator() = this == '\\' || this == '/'
private fun Char.isWinDriveLetter() = this in 'A'..'Z' || this in 'a'..'z'
private fun appendPathWithSlashHandled(path: String, buf: CArrayPointer<WCHARVar>, bufOff: Int = 0) {
    // Windows API before Windows 10 1703 doesn't handle forward and trailing slashes
    var lastWasSep = false
    var ptr = bufOff
    val hasDriverPrefix = path.length >= 2 && path[1] == ':' && path[0].isWinDriveLetter()
    val hasUncPrefix = path.startsWith("\\\\")
    var trailingSlash = path.length
    for (i in path.indices.last downTo if (hasDriverPrefix) 3 else if (hasUncPrefix) 2 else 1) {
        trailingSlash = i
        if (!path[i].isSeparator()) break
    }
    for ((i, ch) in path.withIndex()) {
        if (i > trailingSlash) break
        if (ch == '/') {
            buf[ptr++] = '\\'.code.convert()
            continue
        }
        if (lastWasSep && ch.isSeparator()) {
            when {
                hasDriverPrefix -> if (i > 2) continue // keep the leading "X:\"
                hasUncPrefix -> if (i > 1) continue // keep the leading double slash
                else -> if (i > 0) continue // keep the leading slash
            }
        }
        buf[ptr++] = ch.code.convert()
        lastWasSep = ch.isSeparator()
    }
    buf[ptr] = 0u
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

private fun canonicalizePath(s: String): String {
    memScoped {
        val buf = allocArray<WCHARVar>(s.length)
        appendPathWithSlashHandled(s, buf)
        val r = alloc<PWSTRVar>()
        when (val e = Syscall.PathAllocCanonicalize(buf, 1u, r.ptr)) {
            S_OK -> {}
            E_OUTOFMEMORY -> throw OutOfMemoryError("PathAllocCanonicalize")
            else -> throw InvalidPathException(s, formatErrorCode(e.toUInt()))
        }
        val rs = r.value!!.toKString()
        LocalFree(r.value)
        return rs
    }
}

private fun removeFileSpec(s: String): String {
    memScoped {
        val buf = allocArray<WCHARVar>(s.length + 1)
        appendPathWithSlashHandled(s, buf)
        when (val e = Syscall.PathCchRemoveFileSpec(buf, (s.length + 1).convert())) {
            S_OK -> {}
            E_OUTOFMEMORY -> throw OutOfMemoryError("PathCchRemoveFileSpec")
            else -> throw InvalidPathException(s, formatErrorCode(e.toUInt()))
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

