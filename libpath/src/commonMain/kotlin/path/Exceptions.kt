package path

expect class InvalidPathException : IllegalArgumentException {
    constructor(input: String, reason: String)
}

expect open class IOException : Exception {
    constructor(message: String?)
    constructor(message: String?, cause: Throwable?)
    constructor(cause: Throwable?)
}

expect open class FileSystemException: IOException {
    constructor(file: String?)
    constructor(file: String?, other: String?, reason: String?)
}

expect class NoSuchFileException : FileSystemException {
    constructor(file: String?)
    constructor(file: String?, other: String?, reason: String?)
}

expect class AccessDeniedException : FileSystemException {
    constructor(file: String?)
    constructor(file: String?, other: String?, reason: String?)
}


