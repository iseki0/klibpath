@file:OptIn(ExperimentalForeignApi::class)

package space.iseki.kpath

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.io.Buffer
import kotlinx.io.UnsafeIoApi
import kotlinx.io.unsafe.UnsafeBufferOperations
import platform.windows.CloseHandle
import platform.windows.CreateFileW
import platform.windows.ERROR_HANDLE_EOF
import platform.windows.FALSE
import platform.windows.FILE_BEGIN
import platform.windows.FILE_SHARE_DELETE
import platform.windows.FILE_SHARE_READ
import platform.windows.FILE_SHARE_WRITE
import platform.windows.GENERIC_READ
import platform.windows.GetLastError
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.INVALID_SET_FILE_POINTER
import platform.windows.OPEN_EXISTING
import platform.windows.ReadFile
import platform.windows.SetFilePointer
import platform.windows.TRUE
import platform.windows.WINBOOL
import kotlin.math.min


@OptIn(UnsafeIoApi::class)
internal class WindowsFileSource(val path: String) : FileSource {
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
        memScoped {
            val r: WINBOOL
            val e: Int
            val n = UnsafeBufferOperations.writeToTail(sink, 1) { bytes, startIndexInclusive, endIndexExclusive ->
                val n = alloc<UIntVar>()
                r = bytes.usePinned { pinned ->
                    ReadFile(
                        hFile = h.handle,
                        lpBuffer = pinned.addressOf(startIndexInclusive),
                        nNumberOfBytesToRead = min(
                            (endIndexExclusive - startIndexInclusive).toLong(),
                            byteCount,
                        ).toUInt(),
                        lpNumberOfBytesRead = n.ptr,
                        lpOverlapped = null,
                    )
                }
                e = GetLastError().convert()
                if (r == FALSE && e != ERROR_HANDLE_EOF) {
                    throw translateIOError(code = e, file = path)
                }
                n.value.toInt()
            }
            if (r == TRUE && n == 0 || e == ERROR_HANDLE_EOF) {
                return -1L
            }
            return n.toLong()
        }
    }

    override fun close() {
        h.close()
    }

    override fun toString(): String = "WindowsFileSource(path=\"$path\")"

    override fun seek(position: Long) {
        memScoped {
            val high = alloc<IntVar>()
            high.value = position.ushr(32).toInt()
            val r = SetFilePointer(
                hFile = h.handle,
                lDistanceToMove = position.toInt(),
                lpDistanceToMoveHigh = high.ptr,
                dwMoveMethod = FILE_BEGIN.convert(),
            )
            if (r == INVALID_SET_FILE_POINTER) {
                throw translateIOError(file = path)
            }
        }
    }

}
