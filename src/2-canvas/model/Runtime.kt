package dbest.model

import dbest.adapter.EngineException
import dbest.adapter.TableHandle
import dbest.adapter.closeTable
import dbest.adapter.csvTable
import dbest.adapter.insert
import dbest.adapter.memoryTable
import dbest.adapter.openBTreeTable
import dbest.adapter.xmlTable
import java.util.concurrent.ConcurrentHashMap

class OpenTables : AutoCloseable {
    val open = ConcurrentHashMap<TableSpec, TableHandle>()
    override fun close() = closeTables(this)
}

fun resolve(tables: OpenTables, session: Session, id: TableId): TableHandle {
    val spec = session.tables[id]
    if (spec == null) {
        throw EngineException.PlanError("A tabela ${id.value} nao existe")
    }
    return tables.open.getOrPut(spec, { openTable(spec) })
}

fun closeTables(tables: OpenTables) {
    for (handle in tables.open.values) {
        closeTable(handle)
    }
    tables.open.clear()
}

private fun openTable(spec: TableSpec): TableHandle = when (spec) {
    is MemorySpec -> {
        val table = memoryTable(spec.name, *spec.columns.toTypedArray())
        for (row in spec.rows) {
            insert(table, row)
        }
        table
    }
    is CsvSpec -> csvTable(
        spec.path,
        spec.name,
        *spec.columns.toTypedArray(),
        separator = spec.separator,
        delimiter = spec.delimiter,
        headerLine = spec.headerLine,
    )
    is BTreeSpec -> openBTreeTable(spec.path, spec.cacheSize)
    is XmlSpec -> xmlTable(
        spec.path,
        spec.name,
        *spec.columns.toTypedArray(),
        rootElement = spec.rootElement,
        recordElement = spec.recordElement,
    )
}
