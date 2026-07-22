package dbest.features.export

import dbest.kernel.adapter.SchemaColumn
import dbest.kernel.util.isEmpty

enum class ExportFormat(val extension: String, val contentType: String) {
    CSV("csv", "text/csv; charset=utf-8"),
    SQL("sql", "application/sql; charset=utf-8"),
}

fun exportFormatOf(raw: String): ExportFormat = when (raw.lowercase()) {
    "csv" -> ExportFormat.CSV
    "sql" -> ExportFormat.SQL
    else -> throw IllegalArgumentException("formato de exportacao invalido: '$raw' (use csv ou sql)")
}

fun exportRows(format: ExportFormat, table: String, schema: List<SchemaColumn>, rows: List<Map<String, Any?>>): String =
    when (format) {
        ExportFormat.CSV -> csvExport(schema, rows)
        ExportFormat.SQL -> sqlExport(table, schema, rows)
    }

private const val CRLF: String = "\r\n"

private fun headerNames(schema: List<SchemaColumn>): List<String> {
    val qualify: Boolean = hasDuplicateNames(schema)
    val names: MutableList<String> = ArrayList()
    for (column in schema) {
        names.add(if (qualify) "${column.source}.${column.name}" else column.name)
    }
    return names
}

private fun hasDuplicateNames(schema: List<SchemaColumn>): Boolean {
    val seen: MutableSet<String> = HashSet()
    for (column in schema) {
        if (!seen.add(column.name)) return true
    }
    return false
}

private fun csvExport(schema: List<SchemaColumn>, rows: List<Map<String, Any?>>): String {
    val out = StringBuilder()
    val headers: List<String> = headerNames(schema)
    for (i in headers.indices) {
        if (i != 0) out.append(',')
        out.append(csvField(headers[i]))
    }
    out.append(CRLF)
    for (row in rows) {
        for (i in schema.indices) {
            if (i != 0) out.append(',')
            val column: SchemaColumn = schema[i]
            val value: Any? = row["${column.source}.${column.name}"]
            if (value != null) out.append(csvField(value.toString()))
        }
        out.append(CRLF)
    }
    return out.toString()
}

private fun csvField(value: String): String {
    val needsQuote: Boolean = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
    if (!needsQuote) return value
    return "\"" + value.replace("\"", "\"\"") + "\""
}

private fun sqlExport(table: String, schema: List<SchemaColumn>, rows: List<Map<String, Any?>>): String {
    val out = StringBuilder()
    val headers: List<String> = headerNames(schema)
    out.append(createTable(table, schema, headers))
    for (row in rows) {
        out.append(insertRow(table, schema, headers, row))
    }
    return out.toString()
}

private fun createTable(table: String, schema: List<SchemaColumn>, headers: List<String>): String {
    val out = StringBuilder()
    out.append("CREATE TABLE IF NOT EXISTS ").append(sqlIdentifier(table)).append(" (\n")
    val primaryKeys: MutableList<String> = ArrayList()
    for (i in schema.indices) {
        if (i != 0) out.append(",\n")
        val column: SchemaColumn = schema[i]
        out.append("    ").append(sqlIdentifier(headers[i])).append(' ').append(sqlType(column.type))
        if (column.primaryKey) primaryKeys.add(headers[i])
    }
    if (!isEmpty(primaryKeys)) {
        out.append(",\n    PRIMARY KEY (")
        for (i in primaryKeys.indices) {
            if (i != 0) out.append(", ")
            out.append(sqlIdentifier(primaryKeys[i]))
        }
        out.append(')')
    }
    out.append("\n);\n\n")
    return out.toString()
}

private fun insertRow(table: String, schema: List<SchemaColumn>, headers: List<String>, row: Map<String, Any?>): String {
    val out = StringBuilder()
    out.append("INSERT INTO ").append(sqlIdentifier(table)).append(" (")
    for (i in headers.indices) {
        if (i != 0) out.append(", ")
        out.append(sqlIdentifier(headers[i]))
    }
    out.append(") VALUES (")
    for (i in schema.indices) {
        if (i != 0) out.append(", ")
        val column: SchemaColumn = schema[i]
        out.append(sqlLiteral(row["${column.source}.${column.name}"]))
    }
    out.append(");\n")
    return out.toString()
}

private fun sqlIdentifier(name: String): String = "`" + name.replace("`", "``") + "`"

private fun sqlLiteral(value: Any?): String = when (value) {
    null -> "NULL"
    is Boolean -> if (value) "TRUE" else "FALSE"
    is Number -> value.toString()
    else -> "'" + value.toString().replace("\\", "\\\\").replace("'", "\\'") + "'"
}

private fun sqlType(type: String): String = when (type) {
    "INTEGER" -> "INT"
    "LONG" -> "BIGINT"
    "FLOAT" -> "FLOAT"
    "DOUBLE" -> "DOUBLE"
    "BOOLEAN" -> "BOOLEAN"
    else -> "TEXT"
}
