@file:JvmName("Path")

package path

import kotlin.jvm.JvmName

interface Path {
    val separator: String get() = fileSystem.separator
    val fileSystem: FileSystem

    val isAbsolute: Boolean
    val isRoot: Boolean
    val extension: String? get() = filename?.run { lastIndexOf('.').takeIf { it > -1 }?.let { substring(it + 1) } }

    val parent: Path?
    val filename: String?
    fun join(other: String): Path = join(*arrayOf(other))
    fun join(vararg other: String): Path
    fun canonicalize(): Path
    fun toAbsolute(): Path
    fun evalSymlink(): Path

    companion object
}

@JvmName("of")
expect fun Path(path: String): Path


