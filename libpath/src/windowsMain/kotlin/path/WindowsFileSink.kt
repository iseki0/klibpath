package path

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.UnsafeIoApi
import kotlinx.io.unsafe.UnsafeBufferOperations
import platform.windows.CREATE_ALWAYS
import platform.windows.CREATE_NEW
import platform.windows.CloseHandle
import platform.windows.CreateFileW
import platform.windows.DWORD
import platform.windows.ERROR_ACCESS_DENIED
import platform.windows.FALSE
import platform.windows.FILE_ATTRIBUTE_NORMAL
import platform.windows.FILE_SHARE_DELETE
import platform.windows.FILE_SHARE_READ
import platform.windows.FILE_SHARE_WRITE
import platform.windows.GENERIC_READ
import platform.windows.GENERIC_WRITE
import platform.windows.GetFileAttributesW
import platform.windows.GetLastError
import platform.windows.HANDLE
import platform.windows.INVALID_FILE_ATTRIBUTES
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.OPEN_ALWAYS
import platform.windows.OPEN_EXISTING
import platform.windows.TRUNCATE_EXISTING
import platform.windows.WriteFile
import kotlin.math.min

@OptIn(UnsafeIoApi::class, ExperimentalForeignApi::class)
internal class WindowsFileSink(val path: String, create: Boolean, createNew: Boolean, truncate: Boolean) : RawSink {
    private val h: Handle

    init {
        val dwCreationDisposition = when {
            createNew -> CREATE_NEW
            create && !truncate -> OPEN_ALWAYS
            create && truncate -> CREATE_ALWAYS
            truncate -> TRUNCATE_EXISTING
            else -> OPEN_EXISTING
        }

        tailrec fun c(attr: Int = FILE_ATTRIBUTE_NORMAL, dda: UInt = GENERIC_READ or GENERIC_WRITE.convert()): HANDLE? {
            val h = CreateFileW(
                lpFileName = path,
                dwDesiredAccess = dda,
                dwShareMode = (FILE_SHARE_READ or FILE_SHARE_WRITE or FILE_SHARE_DELETE).convert(),
                lpSecurityAttributes = null,
                dwCreationDisposition = dwCreationDisposition.convert(),
                dwFlagsAndAttributes = attr.convert(),
                hTemplateFile = null,
            )
            if (h != INVALID_HANDLE_VALUE) return h
            val e = GetLastError().toInt()

            /*
             * If CREATE_ALWAYS and FILE_ATTRIBUTE_NORMAL are specified,
             * CreateFile fails and sets the last error to ERROR_ACCESS_DENIED
             * if the file exists and has the FILE_ATTRIBUTE_HIDDEN or FILE_ATTRIBUTE_SYSTEM attribute.
             * To avoid the error, specify the same attributes as the existing file.
             */
            if (dwCreationDisposition == CREATE_ALWAYS && e == ERROR_ACCESS_DENIED && attr == FILE_ATTRIBUTE_NORMAL) {
                val fAttr = GetFileAttributesW(path)
                if (fAttr != INVALID_FILE_ATTRIBUTES && fAttr != FILE_ATTRIBUTE_NORMAL.convert<DWORD>()) {
                    return c(fAttr.convert(), dda)
                }
            }

            /*
             * When an application creates a file across a network, it is better to use GENERIC_READ | GENERIC_WRITE
             * for dwDesiredAccess than to use GENERIC_WRITE alone. The resulting code is faster,
             * because the redirector can use the cache manager and send fewer SMBs with more data.
             * This combination also avoids an issue where writing to a file across a network can occasionally return ERROR_ACCESS_DENIED.
             */
            if (e == ERROR_ACCESS_DENIED && dda != GENERIC_WRITE.toUInt()) {
                return c(attr, GENERIC_WRITE.convert())
            }
            throw translateIOError(file = path, code = e.convert())
        }

        val handle = c()
        try {
            h = Handle(handle)
        } catch (th: Throwable) {
            CloseHandle(handle)
            throw th
        }
    }

    private tailrec fun MemScope.write0(source: Buffer, byteCount: Long, nVar: UIntVar) {
        if (byteCount == 0L) return
        check(byteCount > 0)
        val n = UnsafeBufferOperations.readFromHead(source) { bytes, startIndexInclusive, endIndexExclusive ->
            val r = bytes.usePinned { pinned ->
                val p = pinned.addressOf(startIndexInclusive)
                val maxWrite = min((endIndexExclusive - startIndexInclusive).toLong(), byteCount).toInt()
                WriteFile(
                    hFile = h.handle,
                    lpBuffer = p,
                    nNumberOfBytesToWrite = maxWrite.toUInt(),
                    lpNumberOfBytesWritten = nVar.ptr,
                    lpOverlapped = null,
                )
            }
            if (r == FALSE) {
                throw translateIOError(file = path)
            }
            nVar.value.toInt()
        }
        write0(source, byteCount - n, nVar)
    }

    override fun write(source: Buffer, byteCount: Long) {
        check(!h.isClosed)
        require(byteCount >= 0)
        if (byteCount == 0L) return
        memScoped {
            write0(source, byteCount, alloc())
        }
    }

    override fun flush() {}

    override fun close() {
        h.close()
    }

}
