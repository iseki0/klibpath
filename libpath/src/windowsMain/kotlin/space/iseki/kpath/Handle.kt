@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

package space.iseki.kpath

import kotlinx.cinterop.ExperimentalForeignApi
import platform.windows.CloseHandle
import platform.windows.HANDLE
import platform.windows.INVALID_HANDLE_VALUE
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner

private class HandleInternal(val handle: HANDLE?) : OneTimeClose() {
    override fun doClose() {
        if (handle != INVALID_HANDLE_VALUE) {
            CloseHandle(handle)
        }
    }
}

internal class Handle(handle: HANDLE?) : AutoCloseable {
    private val holder = HandleInternal(handle)

    @Suppress("unused")
    private val cleaner = createCleaner(holder, HandleInternal::close)
    override fun close() {
        holder.close()
    }

    val handle: HANDLE? get() = holder.handle
    val isClosed: Boolean get() = holder.isClosed
}
