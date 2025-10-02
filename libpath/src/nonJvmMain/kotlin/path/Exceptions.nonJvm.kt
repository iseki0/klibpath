package path

actual class InvalidPathException : IllegalArgumentException {
    val input: String
    val reason: String

    actual constructor(input: String, reason: String) {
        this.input = input
        this.reason = reason
    }

    @Suppress("RedundantNullableReturnType")
    override val message: String?
        get() = "$reason: $input"
}

actual class NoSuchFileException : RuntimeException {
    val file: String
    actual constructor(file: String) {
        this.file = file
    }
}

actual class IOException : Exception {
    actual constructor(message: String?) : super(message)
    actual constructor(message: String?, cause: Throwable?) : super(message, cause)
    actual constructor(cause: Throwable?) : super(cause)
}