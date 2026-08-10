package dbest.http

import dbest.adapter.intColumn
import dbest.json.json
import dbest.json.jsonText
import dbest.json.parsedJson
import dbest.model.AddNode
import dbest.model.AddTable
import dbest.model.Command
import dbest.model.MemorySpec
import dbest.model.NodeId
import dbest.model.Position
import dbest.model.ScanNode
import dbest.model.TableId
import kotlinx.serialization.json.jsonArray
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import kotlin.test.Test
import kotlin.test.assertEquals

class PaginationProbe {

    private fun app(): HttpHandler = router(Canvas())

    private fun bigTable(rowCount: Int) = MemorySpec(
        "nums",
        listOf(intColumn("id", primaryKey = true)),
        (0 until rowCount).map { mapOf("id" to it) },
    )

    private fun HttpHandler.command(command: Command): Response =
        this(Request(Method.POST, "/commands").body(jsonText(json(command))))

    private fun rowCount(response: Response) = parsedJson(response.bodyString()).jsonArray.size

    @Test
    fun `probe pagination on a scan of a 300-row table`() {
        val app = app()
        app.command(AddTable(TableId(0), bigTable(300)))
        app.command(AddNode(NodeId(0), ScanNode(TableId(0), "n"), Position(0.0, 0.0)))

        val paged = app(Request(Method.GET, "/nodes/0/rows?offset=0&limit=200"))
        val unpaged = app(Request(Method.GET, "/nodes/0/rows"))

        println("PROBE paged=${rowCount(paged)} unpaged=${rowCount(unpaged)}")
        assertEquals(200, rowCount(paged), "paged request should cap at limit")
    }
}
