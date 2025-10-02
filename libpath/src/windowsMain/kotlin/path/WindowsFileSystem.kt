@file:OptIn(ExperimentalForeignApi::class)

package path

import kotlinx.cinterop.ExperimentalForeignApi
import platform.windows.GetLogicalDrives

internal object WindowsFileSystem : FileSystem {
    override val separator: String get() = "\\"
    override val roots: List<Path>
        get() = run {
            var mask = GetLogicalDrives()
            List(mask.countOneBits()) {
                val ch = 'A' + mask.takeLowestOneBit().countTrailingZeroBits()
                mask = mask and mask.takeLowestOneBit().inv()
                WindowsPath.of("""$ch:\""")
            }
        }


}

