package dbest.kernel.dialogs

import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.UIManager

fun pickDirectory(): Path? {
    val holder = arrayOfNulls<File>(1)
    EventQueue.invokeAndWait {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        } catch (ignored: Exception) {
        }
        val owner = Frame()
        owner.isAlwaysOnTop = true
        val chooser = JFileChooser()
        chooser.dialogTitle = "DBest — escolher a pasta das sessoes"
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        val result = chooser.showDialog(owner, "Escolher")
        owner.dispose()
        if (result == JFileChooser.APPROVE_OPTION) {
            holder[0] = chooser.selectedFile
        }
    }
    return holder[0]?.toPath()
}

fun pickSaveFile(defaultName: String): Path? {
    val holder = arrayOfNulls<File>(1)
    EventQueue.invokeAndWait {
        val owner = Frame()
        owner.isAlwaysOnTop = true
        val dialog = FileDialog(owner, "DBest — salvar sessao", FileDialog.SAVE)
        dialog.file = defaultName
        dialog.isVisible = true
        val directory = dialog.directory
        val file = dialog.file
        dialog.dispose()
        owner.dispose()
        if (directory != null && file != null) {
            holder[0] = File(directory, file)
        }
    }
    return holder[0]?.toPath()
}
