package dbest.features.canvas.query

import dbest.features.canvas.graph.BTreeSpec
import dbest.features.canvas.graph.CsvSpec
import dbest.features.canvas.graph.MemorySpec
import dbest.features.canvas.graph.Session
import dbest.features.canvas.graph.TableId
import dbest.features.canvas.graph.TableSpec
import dbest.features.canvas.graph.XmlSpec
import dbest.kernel.adapter.EngineException
import dbest.kernel.adapter.TableHandle
import dbest.kernel.adapter.closeTable
import dbest.kernel.adapter.csvTable
import dbest.kernel.adapter.insert
import dbest.kernel.adapter.memoryTable
import dbest.kernel.adapter.openBTreeTable
import dbest.kernel.adapter.xmlTable
import java.util.concurrent.ConcurrentHashMap

typealias OpenTables = ConcurrentHashMap<TableSpec, TableHandle>

fun resolve(tables: OpenTables, session: Session, id: TableId): TableHandle {
    val spec = session.tables[id]
    if (spec == null) {
        throw EngineException.PlanError("A tabela ${id.value} nao existe")
    }
    return tables.getOrPut(spec) { openTable(spec) }
}

fun closeTables(tables: OpenTables) {
    for (handle in tables.values) {
        closeTable(handle)
    }
    tables.clear()
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
