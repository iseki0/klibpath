@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

package path

import kotlinx.cinterop.CFunction
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.reinterpret
import platform.posix.size_t
import platform.windows.BOOL
import platform.windows.GetProcAddress
import platform.windows.HMODULE
import platform.windows.HRESULT
import platform.windows.LoadLibraryW
import platform.windows.PCWSTR
import platform.windows.PWSTR
import platform.windows.PWSTRVar
import platform.windows.ULONG
import kotlin.experimental.ExperimentalNativeApi
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

internal object Syscall {
    private fun failed(reason: String): Nothing {
        terminateWithUnhandledException(RuntimeException(reason))
    }

    private fun <T : CPointed> proc(lib: HMODULE) =
        PropertyDelegateProvider<Syscall, ReadOnlyProperty<Syscall, CPointer<T>>> { _, property ->
            ReadOnlyProperty { _, _ ->
                val name = property.name
                val proc = GetProcAddress(lib, name) ?: failed("failed to get $name")
                proc.reinterpret()
            }
        }


    val kernelbase = LoadLibraryW("kernelbase.dll") ?: failed("LoadLibraryW(\"kernelbase.dll\") failed")


    val PathCchIsRoot: PathCchIsRootType by proc(kernelbase)
    val PathAllocCanonicalize: PathAllocCanonicalizeType by proc(kernelbase)
    val PathCchCanonicalizeEx: PathCchCanonicalizeExType by proc(kernelbase)
    val PathCchCombineEx: PathCchCombineExType by proc(kernelbase)
    val PathCchRemoveFileSpec: PathCchRemoveFileSpecType by proc(kernelbase)
}

internal typealias PathCchIsRootType = CPointer<CFunction<(PCWSTR) -> BOOL>>
internal typealias PathAllocCanonicalizeType = CPointer<CFunction<(PCWSTR, ULONG, CPointer<PWSTRVar>) -> HRESULT>>
internal typealias PathCchCanonicalizeExType = CPointer<CFunction<(PWSTR, size_t, PCWSTR, ULONG) -> HRESULT>>
internal typealias PathCchCombineExType = CPointer<CFunction<(PWSTR, size_t, PCWSTR, PCWSTR, ULONG) -> HRESULT>>
internal typealias PathCchRemoveFileSpecType = CPointer<CFunction<(PWSTR, size_t) -> HRESULT>>
