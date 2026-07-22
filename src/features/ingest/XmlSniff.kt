package dbest.features.ingest

import sources.xml.XMLRecognizer

data class XmlGuess(
    val rootElement: String,
    val recordElement: String,
    val columns: List<String>,
    val sampleRows: List<List<String?>>,
    val totalRecords: Int,
)

fun sniffXml(path: String, rootElement: String? = null, recordElement: String? = null): XmlGuess {
    val rootOverride = rootElement?.trim()?.ifEmpty { null }
    val recordOverride = recordElement?.trim()?.ifEmpty { null }
    return try {
        val recognizer = XMLRecognizer(path, rootOverride, recordOverride, XMLRecognizer.FlatteningStrategy.NESTED_COLUMNS)
        val analysis = recognizer.analyzeStructure()
        val columnNames = analysis.columns.map { it.NAME }
        val sampleRows = analysis.sampleData.map { row -> columnNames.map { name -> row[name] } }
        XmlGuess(analysis.rootElement, analysis.recordElement, columnNames, sampleRows, analysis.totalRecords)
    } catch (e: Exception) {
        XmlGuess(rootOverride ?: "", recordOverride ?: "", emptyList(), emptyList(), 0)
    }
}
