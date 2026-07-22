package dbest.features.ingest

import dbest.kernel.adapter.closeTable
import dbest.kernel.adapter.csvTable
import dbest.kernel.adapter.execute
import dbest.kernel.adapter.scan
import dbest.kernel.adapter.stringColumn
import java.io.File

data class CsvGuess(val separator: Char, val headerLine: Int, val columns: List<String>, val sampleRows: List<List<String?>>)

private val SEPARATOR_CANDIDATES = listOf(',', ';', '\t', '|')
private const val SNIFF_SAMPLE_SIZE = 5

fun sniffCsv(path: String, headerLine: Int = 1, separatorOverride: Char? = null): CsvGuess {
    val candidates = if (separatorOverride != null) listOf(separatorOverride) else SEPARATOR_CANDIDATES
    val attempts = candidates.mapNotNull { separator -> tryCandidate(path, separator, headerLine) }
    val best = attempts.maxByOrNull { it.second }?.first
    return best ?: CsvGuess(separatorOverride ?: ',', headerLine, emptyList(), emptyList())
}

private fun tryCandidate(path: String, separator: Char, headerLine: Int): Pair<CsvGuess, Int>? = try {
    val headerText = lineAt(path, headerLine)
    val rawNames = if (headerText != null) splitLine(headerText, separator) else emptyList()
    if (rawNames.size < 2) {
        null
    } else {
        val names = uniqueNames(rawNames)
        val columns = names.map { name -> stringColumn(name, nullable = true) }
        val handle = csvTable(path, "sniff", *columns.toTypedArray(), separator = separator, headerLine = headerLine)
        try {
            val rows = execute(scan(handle, "s"), offset = 0, limit = SNIFF_SAMPLE_SIZE)
            val sample = rows.map { row -> names.map { name -> row["s.$name"] as? String } }
            val consistentRows = rows.count { it.size == names.size }
            val score = names.size * 100 + consistentRows
            CsvGuess(separator, headerLine, names, sample) to score
        } finally {
            closeTable(handle)
        }
    }
} catch (e: Exception) {
    null
}

private fun uniqueNames(names: List<String>): List<String> {
    val seen = HashMap<String, Int>()
    return names.map { raw ->
        val base = raw.ifBlank { "column" }
        val count = seen.getOrDefault(base, 0)
        seen[base] = count + 1
        if (count == 0) base else "${base}_$count"
    }
}

private fun lineAt(path: String, line: Int): String? =
    File(path).bufferedReader().use { reader ->
        var current: String? = null
        repeat(line) { current = reader.readLine() }
        current
    }

internal fun splitLine(line: String, separator: Char): List<String> {
    if (line.isEmpty()) return emptyList()
    val fields = mutableListOf<String>()
    val field = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                field.append('"')
                i++
            }
            c == '"' -> inQuotes = !inQuotes
            c == separator && !inQuotes -> {
                fields.add(field.toString().trim())
                field.clear()
            }
            else -> field.append(c)
        }
        i++
    }
    fields.add(field.toString().trim())
    return fields
}
