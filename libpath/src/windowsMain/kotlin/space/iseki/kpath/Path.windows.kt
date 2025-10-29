package space.iseki.kpath

internal actual fun createPath(path: String): Path = WindowsPath.of(path)
