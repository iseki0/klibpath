@file:OptIn(ExperimentalAtomicApi::class)

package path

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

internal abstract class OneTimeClose() : AutoCloseable {
    private val closed = AtomicBoolean(false)
    abstract fun doClose()
    override fun close() {
        if (closed.compareAndSet(expectedValue = false, newValue = true)) {
            doClose()
        }
    }

    val isClosed: Boolean get() = closed.load()
}

