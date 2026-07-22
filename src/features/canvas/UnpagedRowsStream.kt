package dbest.features.canvas

import dbest.features.sessions.EngineLease
import dbest.kernel.adapter.Plan
import dbest.kernel.adapter.compile.compile
import dbest.kernel.adapter.gate
import dbest.kernel.adapter.rawJson
import dbest.kernel.json.compactJsonText
import ibd.query.Operation
import ibd.query.Tuple
import java.io.InputStream
import java.util.Objects
import kotlin.math.min
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

internal const val ROW_STREAM_BUFFER_BYTES: Int = 64 * 1024

internal fun openUnpagedRowsStream(plan: Plan, lease: EngineLease): InputStream {
    var operation: Operation? = null
    return try {
        gate {
            val opened = compile(plan)
            operation = opened
            val tuples = opened.run()
            CursorNdjsonInputStream(opened, tuples, tupleColumns(opened), lease)
        }
    } catch (failure: Throwable) {
        closeQuietly(operation)
        lease.close()
        throw failure
    }
}

private data class TupleColumn(val rowIndex: Int, val name: String)

private fun tupleColumns(operation: Operation): List<TupleColumn> = buildList {
    for ((rowIndex, source) in operation.exposedDataSources.withIndex()) {
        for (column in source.prototype.columns) {
            add(TupleColumn(rowIndex, column.name))
        }
    }
}

private class CursorNdjsonInputStream(
    private val operation: Operation,
    private val tuples: Iterator<Tuple>,
    private val columns: List<TupleColumn>,
    private val lease: EngineLease,
) : InputStream() {
    private val buffer = ByteArray(ROW_STREAM_BUFFER_BYTES)
    private val singleByte = ByteArray(1)
    private var bufferPosition = 0
    private var bufferLimit = 0
    private var pendingLine = ByteArray(0)
    private var pendingPosition = 0
    private var finished = false

    override fun read(): Int = if (read(singleByte, 0, 1) < 0) -1 else singleByte[0].toInt() and 0xff

    override fun read(target: ByteArray, offset: Int, length: Int): Int {
        Objects.checkFromIndexSize(offset, length, target.size)
        if (length == 0) return 0
        if (bufferPosition == bufferLimit && !fillBuffer()) return -1

        val count = min(length, bufferLimit - bufferPosition)
        buffer.copyInto(target, offset, bufferPosition, bufferPosition + count)
        bufferPosition += count
        return count
    }

    override fun available(): Int = bufferLimit - bufferPosition

    override fun close() {
        bufferPosition = bufferLimit
        pendingLine = ByteArray(0)
        pendingPosition = 0
        finish()
    }

    private fun fillBuffer(): Boolean {
        if (finished) return false
        bufferPosition = 0
        bufferLimit = 0

        try {
            gate {
                while (bufferLimit < buffer.size) {
                    if (pendingPosition == pendingLine.size) {
                        if (!tuples.hasNext()) {
                            finish()
                            break
                        }
                        pendingLine = encodeLine(tuples.next(), columns)
                        pendingPosition = 0
                    }

                    val count = min(buffer.size - bufferLimit, pendingLine.size - pendingPosition)
                    pendingLine.copyInto(buffer, bufferLimit, pendingPosition, pendingPosition + count)
                    bufferLimit += count
                    pendingPosition += count
                }
            }
        } catch (failure: Throwable) {
            finish()
            throw failure
        }

        return bufferLimit > 0
    }

    private fun finish() {
        if (finished) return
        finished = true
        try {
            closeQuietly(operation)
        } finally {
            lease.close()
        }
    }
}

private fun encodeLine(tuple: Tuple, columns: List<TupleColumn>): ByteArray {
    val values = ArrayList<JsonElement>(columns.size)
    for (column in columns) {
        values.add(rawJson(tuple.rows[column.rowIndex].getValue(column.name)))
    }
    return (compactJsonText(JsonArray(values)) + "\n").toByteArray(Charsets.UTF_8)
}

private fun closeQuietly(operation: Operation?) {
    try {
        operation?.close()
    } catch (_: Exception) {
    }
}
