package path

actual fun Path(path: String): Path = WindowsPath.of(path)
