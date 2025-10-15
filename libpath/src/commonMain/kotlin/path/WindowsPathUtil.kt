package path

import kotlin.jvm.JvmInline

internal data object WindowsPathUtil {
    private fun invalidPath(
        input: CharSequence, reason: String,
    ) = InvalidPathException(input.toString(), reason)

    private fun Char.isSeparator() = this == '\\' || this == '/'

    private fun Char.isWinDriveLetter() = this in 'A'..'Z' || this in 'a'..'z'

    private fun Char.isPathForbidden(): Boolean = when (this) {
        '<', '>', ':', '"', '/', '\\', '|', '?', '*' -> true
        else -> this.code in 0x00..0x1F
    }

    private fun CharSequence.endWithDoubleDotSegment(index: Int): Boolean =
        this[index] == '.' && index - 2 > 0 && this[index - 1] == '.' && this[index - 2].isPathForbidden()

    private fun CharSequence.endWithSingleDotSegment(index: Int): Boolean =
        this[index] == '.' && index - 1 > 0 && this[index - 1].isPathForbidden()

    private fun CharSequence.findLastSeparatorInPath(end: Int, start: Int): Int {
        for (i in end downTo start) {
            if (this[i].isSeparator()) return i
            if (this[i].isPathForbidden()) throwForbiddenCharInPath(i)
        }
        return -1
    }

    private fun CharSequence.throwForbiddenCharInPath(index: Int): Nothing {
        throw invalidPath(this, "Filename contains forbidden character at $index: ${this[index]}")
    }

    private fun CharSequence.findPathStartOfUnc(isLong: Boolean): Int {
        // Determine the prefix length: \\?\UNC\ (8) for long UNC paths, \\ (2) for normal UNC paths
        val startIndex = if (isLong) 8 else 2
        if (length <= startIndex) throw invalidPath(this, "UNC path is missing hostname")

        var i = startIndex

        // Scans a single UNC segment (e.g., server name or share name) until '\' or end of string.
        // Also checks for any forbidden characters.
        fun scanSegment(start: Int): Int {
            var pos = start
            while (pos < length) {
                val ch = this[pos]
                if (ch == '\\') break
                if (ch.isPathForbidden()) {
                    throwForbiddenCharInPath(pos)
                }
                pos++
            }
            return pos
        }

        // Step 1: Scan the server name segment
        i = scanSegment(i)
        if (i == startIndex) {
            throw invalidPath(this, "The UNC path is missing a server name.")
        }

        // Step 2: There must be a '\' after the server segment
        if (i >= length || this[i] != '\\') return -1
        i++ // Skip '\'

        // Step 3: Scan the share name segment
        val shareStart = i
        i = scanSegment(i)
        if (i == shareStart) {
            throw invalidPath(this, "The UNC path is missing a share name.")
        }

        return i
    }


    @JvmInline
    internal value class PathProp private constructor(private val v: ULong) {
        companion object {
            const val IS_LONG_BIT = 1L shl 40
            const val IS_UNC_BIT = 1L shl 41
            const val HAS_DOS_PREFIX_BIT = 1L shl 42
            const val IS_ABS_BIT = 1L shl 43
            const val IS_NO_PATH_BIT = 1L shl 44

            fun analyze(input: CharSequence): PathProp {
                val isLongPath: Boolean
                val isUncPath: Boolean
                var start = 0
                when {
                    input.startsWith("""\\?\UNC\""") -> {
                        start = input.findPathStartOfUnc(isLong = true)
                        isLongPath = true
                        isUncPath = true
                    }

                    input.startsWith("""\\?\""") -> {
                        start = 4
                        isLongPath = true
                        isUncPath = false
                    }

                    input.length >= 2 && input[0].isSeparator() && input[1].isSeparator() -> {
                        start = input.findPathStartOfUnc(isLong = false)
                        isLongPath = false
                        isUncPath = true
                    }

                    else -> {
                        isLongPath = false
                        isUncPath = false
                    }
                }
                val hasDosPrefix: Boolean
                if (!isUncPath && input.length >= start + 2 && input[start].isWinDriveLetter() && input[start + 1] == ':') {
                    start += 2
                    hasDosPrefix = true
                } else {
                    hasDosPrefix = false
                }

                val isAbsolute = start <= input.lastIndex && input[start].isSeparator()
                val isNoPath = start > input.lastIndex
                var v = start.toUInt().toLong()
                v = v or if (isLongPath) IS_LONG_BIT else 0L
                v = v or if (isUncPath) IS_UNC_BIT else 0L
                v = v or if (hasDosPrefix) HAS_DOS_PREFIX_BIT else 0L
                v = v or if (isAbsolute) IS_ABS_BIT else 0L
                v = v or if (isNoPath) IS_NO_PATH_BIT else 0L
                return PathProp(v.toULong())
            }
        }

        private fun testb(bit: Long) = v.toLong() and bit != 0L

        /**
         * Path has long path prefix (e.g., `\\?\`).
         */
        val isLong: Boolean get() = testb(IS_LONG_BIT)

        /**
         * Path is a UNC path (e.g., `\\server\share`).
         */
        val isUNC: Boolean get() = testb(IS_UNC_BIT)

        /**
         * Path has DOS drive letter prefix (e.g., `C:`).
         */
        val hasDosPrefix: Boolean get() = testb(HAS_DOS_PREFIX_BIT)

        /**
         * Path is absolute (e.g., `C:\folder` or `\\server\share`).
         */
        val isAbs: Boolean get() = testb(IS_ABS_BIT)

        /**
         * Path contains no segments (e.g., `C:` or `\\server\share`).
         */
        val isNoPath: Boolean get() = testb(IS_NO_PATH_BIT)

        /**
         * The index of the first character after the prefix (e.g., after `C:` or `\\server\share`).
         */
        val start: Int get() = v.toUInt().toInt()
    }

    fun normalizePath(input: CharSequence, collapse: Boolean = true): String {
        if (input.isEmpty()) return ""
        val buf = CharArray(input.length + 1)
        val props = PathProp.analyze(input)
        val start = props.start
        val isLongPath = props.isLong
        val isUncPath = props.isUNC
        val hasDosPrefix = props.hasDosPrefix
        val isAbsolute = props.isAbs
        val isNoPath = props.isNoPath

        if (!isAbsolute && isUncPath && !isNoPath) {
            throw invalidPath(input, "UNC path must be absolute")
        }
        if (!isAbsolute && isLongPath) {
            throw invalidPath(input, "Long path prefix is only supported for absolute paths")
        }

        var isSep = true
        var p = buf.lastIndex
        var i = input.lastIndex
        var skipLevel = 0
        while (i >= start) {
            val ch = input[i]
            if (ch.isSeparator() && isSep) {
                i--
                continue
            }
            if (ch.isSeparator()) {
                buf[p--] = '\\'
                i--
                isSep = true
                continue
            }
            if (ch.isPathForbidden()) {
                input.throwForbiddenCharInPath(i)
            }
            if (ch == '.' && collapse) {
                if (input.endWithDoubleDotSegment(i)) {
                    skipLevel++
                    i -= 3 // skip \..
                    continue
                }
                if (input.endWithSingleDotSegment(i)) {
                    i -= 2 // skip \.
                    continue
                }
            }
            if (skipLevel > 0) {
                val ii = input.findLastSeparatorInPath(i, start)
                i = if (ii == -1) {
                    // cannot go back anymore
                    start - 1
                } else {
                    ii - 1
                }
                skipLevel--
                continue
            }
            isSep = ch.isSeparator()
            buf[p--] = ch
            i--
        }

        if (!isAbsolute) {
            repeat(skipLevel) {
                buf[p--] = '.'
                buf[p--] = '.'
                if (it != skipLevel - 1) {
                    buf[p--] = '\\'
                }
            }
        }

        // handle the prefix \, check is abs
        val alreadyWriteSlash = p + 1 <= buf.lastIndex && buf[p + 1] == '\\'
        if (isAbsolute && !alreadyWriteSlash || isNoPath) {
            buf[p--] = '\\'
        } else if (!isAbsolute && alreadyWriteSlash) {
            buf[p++] = 0.toChar()
        }

        // copy the prefix
        for (i in start - 1 downTo 0) {
            buf[p--] = input[i]
        }
        if (hasDosPrefix && buf[p + start - 1].isLowerCase()) {
            buf[p + start - 1] = buf[p + start - 1].uppercaseChar()
        }
        val offset = p + 1
        return buf.concatToString(offset).let(::normalizePathForInMaxPathConvention)
    }

    private fun normalizePathForInMaxPathConvention(input: CharSequence): String {
        if (input.isEmpty()) return ""
        val props = PathProp.analyze(input)
        if (!props.isAbs) return input.toString()
        if (props.isLong) {
            if (props.isUNC) {
                if (input.length < 264) {
                    return '\\' + input.substring(7)
                }
                return input.toString()
            }
            if (input.length < 264) {
                return input.substring(4)
            }
            return input.toString()
        }
        if (input.length >= 260) {
            if (props.isUNC) {
                return """\\?\UNC\""" + input.substring(2)
            }
            return """\\?\$input"""
        }
        return input.toString()

    }

    fun getFilenameIndex(path: CharSequence): Int {
        val props = PathProp.analyze(path)
        if (props.isNoPath) return -1
        val i = path.lastIndexOf('\\')
        if (props.start == path.lastIndex && path[props.start] == '\\') return -1
        if (i < props.start) {
            return props.start
        }
        return i + 1
    }

    fun joinPath(base: CharSequence, other: CharSequence): String {
        if (other.isEmpty()) {
            return base.toString()
        }
        if (base.isEmpty()) {
            return other.toString()
        }
        var base = base
        var other = other
        val otherProps = PathProp.analyze(other)
        val baseProps = PathProp.analyze(base)
        val otherDriver = otherProps.start.let { other.take(it) }
        val baseDriver = baseProps.start.let { base.take(it) }
        if (otherProps.isAbs) {
            if (otherDriver != "" || baseDriver == "") {
                return other.toString()
            }
            base = baseDriver
        } else {
            if (otherDriver == baseDriver) {
                other = other.drop(otherProps.start)
            } else if (otherDriver != "") {
                return other.toString()
            }
        }
        return when {
            base.endsWith('\\') && other.startsWith('\\') -> "$base${other.substring(1)}"
            base.endsWith('\\') || other.startsWith('\\') -> "$base$other"
            else -> "$base\\$other"
        }
    }

}

