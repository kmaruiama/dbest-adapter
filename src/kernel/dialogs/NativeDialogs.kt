package dbest.kernel.dialogs

import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

internal data class Picked(val path: String, val fileName: String)

internal fun openNativeDialog(): Picked? {
    val holder = arrayOfNulls<Picked>(1)
    EventQueue.invokeAndWait {
        val owner = Frame()
        owner.isAlwaysOnTop = true
        val dialog = FileDialog(owner, "DBest — escolher tabela (.csv / .xml / .head / .dat)", FileDialog.LOAD)
        dialog.isVisible = true
        val directory = dialog.directory
        val file = dialog.file
        dialog.dispose()
        owner.dispose()
        if (directory != null && file != null) {
            holder[0] = Picked(File(directory, file).absolutePath, file)
        }
    }
    return holder[0]
}

internal fun baseName(fileName: String): String {
    val dot = fileName.lastIndexOf('.')
    return if (dot > 0) fileName.substring(0, dot) else fileName
}
