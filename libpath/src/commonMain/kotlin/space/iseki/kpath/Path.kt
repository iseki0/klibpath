package space.iseki.kpath

import kotlin.jvm.JvmName
import kotlin.jvm.JvmStatic

interface Path {
    val separator: String get() = fileSystem.separator
    val fileSystem: FileSystem

    val isAbsolute: Boolean
    val extension: String? get() = filename?.run { lastIndexOf('.').takeIf { it > -1 }?.let { substring(it + 1) } }

    val parent: Path?
    val filename: String?
    fun join(other: String): Path = join(*arrayOf(other))
    fun join(vararg other: String): Path
    fun normalization(): Path
    fun toAbsolute(): Path

    companion object {
        @JvmStatic
        @JvmName("of")
        operator fun invoke(path: String) = createPath(path)
    }
}

internal expect fun createPath(path: String): Path


