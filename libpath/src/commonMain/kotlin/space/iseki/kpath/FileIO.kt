package space.iseki.kpath

import kotlinx.io.RawSink
import kotlinx.io.RawSource

interface Seekable {
    fun seek(position: Long)
}

interface FileSink : RawSink, Seekable

interface FileSource : RawSource, Seekable
