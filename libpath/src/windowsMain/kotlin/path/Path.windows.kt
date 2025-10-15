package path

internal actual fun createPath(path: String): Path = WindowsPath.of(path)
