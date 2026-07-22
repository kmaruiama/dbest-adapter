package dbest.kernel.util

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

fun writeAtomically(target: Path, text: String) {
    val absolute = target.toAbsolutePath()
    Files.createDirectories(absolute.parent)
    val tmp = absolute.resolveSibling(absolute.fileName.toString() + ".tmp")
    Files.writeString(tmp, text)
    try {
        Files.move(tmp, absolute, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } catch (e: AtomicMoveNotSupportedException) {
        Files.move(tmp, absolute, StandardCopyOption.REPLACE_EXISTING)
    }
}
