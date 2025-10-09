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

actual open class IOException : Exception {
    actual constructor(message: String?) : super(message)
    actual constructor(message: String?, cause: Throwable?) : super(message, cause)
    actual constructor(cause: Throwable?) : super(cause)
}

actual open class FileSystemException : IOException {
    private val file: String?
    private val other: String?
    private val reason: String?

    actual constructor(file: String?) : this(file, null, null)
    actual constructor(file: String?, other: String?, reason: String?) : super(reason) {
        this.file = file
        this.other = other
        this.reason = reason
    }

    override val message: String?
        get() = buildString(capacity = (file?.length ?: 0) + (other?.length ?: 0) + (reason?.length ?: 0) + 8) {
            if (file != null) {
                append(file)
            }
            if (other != null) {
                append(" -> ").append(other)
            }
            if (reason != null) {
                append(": ").append(reason)
            }
        }

}

actual class NoSuchFileException : FileSystemException {
    actual constructor(file: String?) : super(file)
    actual constructor(file: String?, other: String?, reason: String?): super(file, other, reason)
}

actual class AccessDeniedException : FileSystemException {
    actual constructor(file: String?) : super(file)
    actual constructor(file: String?, other: String?, reason: String?) : super(file, other, reason)
}
