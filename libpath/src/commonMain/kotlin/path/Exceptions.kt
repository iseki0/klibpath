package path

expect class InvalidPathException : IllegalArgumentException {
    constructor(input: String, reason: String)
}

expect class NoSuchFileException : Exception {
    constructor(file: String)
}

expect class IOException : Exception {
    constructor(message: String?)
    constructor(message: String?, cause: Throwable?)
    constructor(cause: Throwable?)
}
