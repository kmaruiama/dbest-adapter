package dbest.export

import dbest.adapter.SchemaColumn
import dbest.misc.isEmpty

/*
  Exportacao do resultado de um node para texto. Transformacao pura (schema, rows) -> String:
  nao toca a engine (ibd.*) nem faz IO — consome apenas o vocabulario do adapter (SchemaColumn)
  e as linhas ja drenadas (List<Map<"source.name", valor>>). A entrega HTTP mora em dbest.http.

  Duas formas puras e sem dependencia extra: CSV (RFC 4180) e SQL (CREATE TABLE + INSERTs, dialeto
  padrao ANSI: identificadores entre aspas duplas, literais entre aspas simples com '' escapado).
  Excel/XML/DAT ficam como pontos de extensao — adicionar uma variante quebra o when de proposito.

  Nome exibido de cada coluna (cabecalho CSV / identificador SQL): segue a regra do exportToCSV
  legado — nome simples (col.name) quando todos sao unicos; se DUAS colunas colidirem no nome
  simples, TODAS passam a usar "source.name" qualificado (tudo-ou-nada, mantendo o cabecalho
  consistente e os identificadores SQL unicos, ex.: um join que expoe u.id e o.id). O valor eh
  sempre buscado na linha pela chave qualificada "source.name" (a mesma que a engine emite em toRow).
*/

enum class ExportFormat(val extension: String, val contentType: String) {
    CSV("csv", "text/csv; charset=utf-8"),
    SQL("sql", "application/sql; charset=utf-8"),
}

// fabrica: nome do formato (query ?format=) -> ExportFormat; desconhecido eh 400 no errorFilter
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

// nome exibido de cada coluna: simples quando todos sao unicos; havendo colisao, TODOS ficam
// "source.name" (regra tudo-ou-nada do exportToCSV legado). Ordem sempre a do schema.
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
    // cabecalho: nomes exibidos na ordem do schema
    for (i in headers.indices) {
        if (i != 0) out.append(',')
        out.append(csvField(headers[i]))
    }
    out.append(CRLF)
    // linhas: valor buscado pela chave qualificada "source.name"; null vira campo vazio
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

// RFC 4180: cita o campo quando ele contem , " CR ou LF, e dobra aspas internas
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
    out.append("CREATE TABLE ").append(sqlIdentifier(table)).append(" (\n")
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

// identificador ANSI: entre aspas duplas, aspas internas dobradas
private fun sqlIdentifier(name: String): String = "\"" + name.replace("\"", "\"\"") + "\""

// literal por tipo do VALOR (nao do schema): numeros crus, boolean TRUE/FALSE, null NULL,
// o resto vira string entre aspas simples com '' escapado
private fun sqlLiteral(value: Any?): String = when (value) {
    null -> "NULL"
    is Boolean -> if (value) "TRUE" else "FALSE"
    is Number -> value.toString()
    else -> "'" + value.toString().replace("'", "''") + "'"
}

// tipo do schema (string vinda da engine) -> tipo SQL padrao; desconhecido vira VARCHAR(255)
private fun sqlType(type: String): String = when (type) {
    "INTEGER" -> "INTEGER"
    "LONG" -> "BIGINT"
    "FLOAT" -> "REAL"
    "DOUBLE" -> "DOUBLE PRECISION"
    "BOOLEAN" -> "BOOLEAN"
    else -> "VARCHAR(255)"
}
