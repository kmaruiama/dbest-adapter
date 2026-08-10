package dbest.model

import dbest.adapter.Column

sealed interface TableSpec {
    val name: String
}

data class MemorySpec(
    override val name: String,
    val columns: List<Column>,
    val rows: List<Map<String, Any?>> = emptyList(),
) : TableSpec

data class CsvSpec(override val name: String, val path: String, val columns: List<Column>, val separator: Char = ',', val delimiter: Char = '"', val headerLine: Int = 1) : TableSpec

data class BTreeSpec(override val name: String, val path: String, val cacheSize: Int = 100_000) : TableSpec

data class XmlSpec(
    override val name: String,
    val path: String,
    val columns: List<Column>,
    val rootElement: String? = null,
    val recordElement: String? = null,
) : TableSpec
