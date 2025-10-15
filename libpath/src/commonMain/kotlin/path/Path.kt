@file:JvmName("Path")

package path

import kotlin.jvm.JvmName

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

    companion object
}

@JvmName("of")
fun Path(path: String): Path = createPath(path)

internal expect fun createPath(path: String): Path


