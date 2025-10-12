package path

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.windows.ERROR_ACCESS_DENIED
import platform.windows.ERROR_DIRECTORY
import platform.windows.ERROR_FILE_NOT_FOUND
import platform.windows.ERROR_MUI_FILE_NOT_FOUND
import platform.windows.ERROR_NOT_ENOUGH_MEMORY
import platform.windows.ERROR_NOT_SUPPORTED
import platform.windows.ERROR_PATH_NOT_FOUND
import platform.windows.FORMAT_MESSAGE_ALLOCATE_BUFFER
import platform.windows.FORMAT_MESSAGE_FROM_SYSTEM
import platform.windows.FORMAT_MESSAGE_IGNORE_INSERTS
import platform.windows.FormatMessageW
import platform.windows.GetLastError
import platform.windows.LPWSTRVar
import platform.windows.LocalFree

@OptIn(ExperimentalForeignApi::class)
internal fun formatErrorCode(code: UInt = GetLastError()): String {
    memScoped {
        val r = alloc<LPWSTRVar>()
        val n = FormatMessageW(
            dwFlags = (FORMAT_MESSAGE_ALLOCATE_BUFFER or FORMAT_MESSAGE_IGNORE_INSERTS or FORMAT_MESSAGE_FROM_SYSTEM).toUInt(),
            lpSource = null,
            dwMessageId = code,
            dwLanguageId = 0u,
            lpBuffer = r.ptr.reinterpret(),
            nSize = 0u,
            Arguments = null,
        )
        if (n == 0u) {
            return "unknown error (0x${code.toHexString()})"
        }
        val s = r.value!!.toKString().trimEnd()
        LocalFree(r.value)
        return "$s (0x${code.toHexString()})"
    }
}

internal fun translateIOError(code: UInt = GetLastError(), file: String? = null, other: String? = null): Throwable {
    return when (code.toInt()) {
        ERROR_PATH_NOT_FOUND,
        ERROR_FILE_NOT_FOUND,
        ERROR_MUI_FILE_NOT_FOUND,
            -> NoSuchFileException(file, null, formatErrorCode(code))

        ERROR_ACCESS_DENIED,
        1314, // A required privilege is not held by the client.
            -> AccessDeniedException(file, other, formatErrorCode(code))

        ERROR_NOT_ENOUGH_MEMORY -> OutOfMemoryError()
        ERROR_NOT_SUPPORTED -> UnsupportedOperationException(formatErrorCode(code))
        ERROR_DIRECTORY -> NotDirectoryException(file)
        else -> IOException(formatErrorCode(code))
    }
}
