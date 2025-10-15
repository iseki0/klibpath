package path

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import platform.windows.GetFullPathNameW
import platform.windows.MAX_PATH
import platform.windows.WCHARVar

@OptIn(ExperimentalForeignApi::class)
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

    override val isAbsolute: Boolean get() = WindowsPathUtil.PathProp.analyze(value).isAbs

    override val parent: Path?
        get() = if (filenameIdx == -1) null else of(value.substring(0, filenameIdx))

    override val filename: String?
        get() = if (filenameIdx != -1) value.substring(filenameIdx) else null


    override fun join(vararg other: String): Path = other.fold(value) { base, other ->
        WindowsPathUtil.joinPath(base, WindowsPathUtil.normalizePath(other, collapse = false))
    }.let(::of)

    override fun normalization(): Path = ofNormalized(WindowsPathUtil.normalizePath(value))

    override fun toAbsolute(): Path {
        memScoped {
            var buf = allocArray<WCHARVar>(MAX_PATH)
            var n = GetFullPathNameW(value, MAX_PATH.convert(), buf, null)
            if (n >= MAX_PATH.convert()) {
                buf = allocArray<WCHARVar>(n.convert())
                n = GetFullPathNameW(value, n, buf, null)
            }
            if (n == 0u) {
                throw InvalidPathException(value, formatErrorCode())
            }
            return of(buf.toKString())
        }
    }

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

