package dbest.kernel.adapter

import dbest.kernel.util.existsInCollection
import dbest.kernel.util.isBlank
import dbest.kernel.util.isEmpty
import dbest.kernel.util.mapCollection
import dbest.kernel.util.varargToCollection
import ibd.table.Table
import ibd.table.btree.BTreeTable
import ibd.table.csv.CSVTable
import ibd.table.memory.MemoryTable
import ibd.table.prototype.BasicDataRow
import ibd.table.prototype.Header
import ibd.table.prototype.Prototype
import ibd.table.prototype.column.BooleanColumn
import ibd.table.prototype.column.Column as IbdColumn
import ibd.table.prototype.column.DoubleColumn
import ibd.table.prototype.column.FloatColumn
import ibd.table.prototype.column.IntegerColumn
import ibd.table.prototype.column.LongColumn
import ibd.table.prototype.column.StringColumn
import ibd.table.prototype.metadata.Metadata
import ibd.table.xml.XMLTable
import sources.xml.XMLRecognizer

enum class ColumnType { INT, LONG, FLOAT, DOUBLE, STRING, BOOLEAN }

data class Column(val name: String, val type: ColumnType, val primaryKey: Boolean, val nullable: Boolean) {
    init {
        require(!isBlank(name), { "O nome da coluna nao pode ser vazio" })
    }
}

fun intColumn(name: String, primaryKey: Boolean = false, nullable: Boolean = false): Column = Column(name, ColumnType.INT, primaryKey, nullable)
fun longColumn(name: String, primaryKey: Boolean = false, nullable: Boolean = false): Column = Column(name, ColumnType.LONG, primaryKey, nullable)
fun floatColumn(name: String, nullable: Boolean = false): Column = Column(name, ColumnType.FLOAT, primaryKey = false, nullable)
fun doubleColumn(name: String, nullable: Boolean = false): Column = Column(name, ColumnType.DOUBLE, primaryKey = false, nullable)
fun stringColumn(name: String, primaryKey: Boolean = false, nullable: Boolean = false): Column = Column(name, ColumnType.STRING, primaryKey, nullable)
fun booleanColumn(name: String, nullable: Boolean = false): Column = Column(name, ColumnType.BOOLEAN, primaryKey = false, nullable)

class TableHandle internal constructor(internal val table: Table)

fun memoryTable(name: String, vararg columns: Column): TableHandle = gate {
    val proto: Prototype = prototype(*columns)
    val header: Header = Header(proto, name)
    val table: MemoryTable = MemoryTable(header)
    TableHandle(table)
}

fun csvTable(path: String, name: String, vararg columns: Column, separator: Char = ',', delimiter: Char = '"', headerLine: Int = 1): TableHandle = gate {
    val header: Header = Header(prototype(*columns), name)
    header.set(Header.FILE_PATH, path)
    val table: CSVTable = CSVTable(header, separator, delimiter, headerLine)
    table.open()
    TableHandle(table)
}

fun xmlTable(path: String, name: String, vararg columns: Column, rootElement: String? = null, recordElement: String? = null): TableHandle = gate {
    val header: Header = Header(prototype(*columns), name)
    header.set(Header.FILE_PATH, path)
    val table: XMLTable = XMLTable(header, rootElement, recordElement, XMLRecognizer.FlatteningStrategy.NESTED_COLUMNS)
    table.open()
    TableHandle(table)
}

fun openBTreeTable(path: String, cacheSize: Int = 100_000): TableHandle = gate {
    val table: BTreeTable = BTreeTable(path, cacheSize)
    table.open()
    TableHandle(table)
}

fun tableName(table: TableHandle): String = table.table.name

fun columns(table: TableHandle): List<Column> =
    mapCollection(table.table.prototype.columns) { column ->
        Column(column.name, columnType(column.type), column.isPrimaryKey, nullable = false)
    }

fun insert(table: TableHandle, values: Map<String, Any?>, unique: Boolean = true) {
    val known: MutableSet<String> = HashSet()
    for (column in columns(table)) {
        known.add(column.name)
    }
    val row: BasicDataRow = BasicDataRow()
    for ((column, value) in values) {
        if (!existsInCollection(column, known)) {
            throw EngineException.PlanError("A tabela '${tableName(table)}' nao tem a coluna '$column'")
        }
        setField(row, column, value)
    }
    gate { table.table.addRecord(row, unique) }
}

fun closeTable(table: TableHandle) {
    gate { table.table.close() }
}

private fun setField(row: BasicDataRow, column: String, value: Any?) {
    when (value) {
        null -> {}
        is Int -> row.setInt(column, value)
        is Long -> row.setLong(column, value)
        is Float -> row.setFloat(column, value)
        is Double -> row.setDouble(column, value)
        is Boolean -> row.setBoolean(column, value)
        is String -> row.setString(column, value)
        else -> throw EngineException.PlanError("Tipo de valor nao suportado ${value.javaClass.simpleName} para a coluna $column")
    }
}

private fun columnType(engineType: String): ColumnType = when (engineType) {
    "INTEGER" -> ColumnType.INT
    "LONG" -> ColumnType.LONG
    "FLOAT" -> ColumnType.FLOAT
    "DOUBLE" -> ColumnType.DOUBLE
    "BOOLEAN" -> ColumnType.BOOLEAN
    else -> ColumnType.STRING
}

private fun prototype(vararg columns: Column): Prototype {
    val columnList: Collection<Column> = varargToCollection(*columns)
    require(!isEmpty(columnList), { "A tabela precisa de no minimo uma coluna" })
    val prototype: Prototype = Prototype()
    for (column in columnList) {
        prototype.addColumn(engineColumn(column))
    }
    return prototype
}

private fun engineColumn(column: Column): IbdColumn {
    val base: Short = baseFlags(column)
    return when (column.type) {
        ColumnType.INT -> IntegerColumn(column.name, 4, base)
        ColumnType.LONG -> LongColumn(column.name, 8, base)
        ColumnType.FLOAT -> FloatColumn(column.name, 4, orFlags(base, Metadata.FLOATING_POINT))
        ColumnType.DOUBLE -> DoubleColumn(column.name, 8, orFlags(base, Metadata.FLOATING_POINT))
        ColumnType.BOOLEAN -> BooleanColumn(column.name, 1, orFlags(base, Metadata.BOOLEAN))
        ColumnType.STRING -> StringColumn(column.name, 255, orFlags(base, Metadata.STRING, Metadata.DINAMIC_COLUMN_SIZE))
    }
}

private fun baseFlags(column: Column): Short = when {
    column.primaryKey -> Metadata.PRIMARY_KEY
    column.nullable -> Metadata.CAN_NULL_COLUMN
    else -> Metadata.NONE
}

private fun orFlags(vararg flags: Short): Short {
    var combined: Int = 0
    for (flag in flags) {
        combined = combined or flag.toInt()
    }
    return combined.toShort()
}
