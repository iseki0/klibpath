@file:OptIn(ExperimentalForeignApi::class)

package path

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import platform.windows.CloseHandle
import platform.windows.CreateFileW
import platform.windows.ERROR_HANDLE_EOF
import platform.windows.FALSE
import platform.windows.FILE_SHARE_DELETE
import platform.windows.FILE_SHARE_READ
import platform.windows.FILE_SHARE_WRITE
import platform.windows.GENERIC_READ
import platform.windows.GetLastError
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.OPEN_EXISTING
import platform.windows.ReadFile
import kotlin.math.min

private const val MAX_BUFFER_SIZE = 64 * 1024L
private val EEOF = ERROR_HANDLE_EOF.toUInt()

internal class WindowsFileSource(val path: String) : RawSource {
    private val h: Handle

    init {
        val handle = CreateFileW(
            lpFileName = path,
            dwDesiredAccess = GENERIC_READ,
            dwShareMode = (FILE_SHARE_READ or FILE_SHARE_WRITE or FILE_SHARE_DELETE).toUInt(),
            lpSecurityAttributes = null,
            dwCreationDisposition = OPEN_EXISTING.toUInt(),
            dwFlagsAndAttributes = 0u,
            hTemplateFile = null,
        )
        if (handle == INVALID_HANDLE_VALUE) {
            throw translateIOError(file = path)
        }
        try {
            h = Handle(handle)
        } catch (th: Throwable) {
            CloseHandle(handle)
            throw th
        }
    }

    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        check(!h.isClosed)
        require(byteCount >= 0)
        if (byteCount == 0L) return 0L
        val buf = ByteArray(min(byteCount, MAX_BUFFER_SIZE).toInt())
        memScoped {
            val n = alloc<UIntVar>()
            val r = buf.usePinned { pinned ->
                ReadFile(
                    hFile = h.handle,
                    lpBuffer = pinned.addressOf(0),
                    nNumberOfBytesToRead = buf.size.toUInt(),
                    lpNumberOfBytesRead = n.ptr,
                    lpOverlapped = null,
                )
            }
            val e = GetLastError()
            val v = n.value.toInt()
            if (v > 0) sink.write(buf, 0, v)
            if (r == FALSE && e == EEOF || v == 0) return -1
            if (r == FALSE && e != EEOF) throw translateIOError(file = path, code = e)
            return v.toLong()
        }
    }

    override fun close() {
        h.close()
    }

    override fun toString(): String = "WindowsFileSource(path=\"$path\")"

}
