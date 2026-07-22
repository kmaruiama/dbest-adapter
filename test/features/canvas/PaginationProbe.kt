package dbest.features.canvas

import dbest.features.canvas.graph.MemorySpec
import dbest.features.canvas.graph.NodeId
import dbest.features.canvas.graph.Position
import dbest.features.canvas.graph.ScanNode
import dbest.features.canvas.graph.TableId
import dbest.features.canvas.history.AddNode
import dbest.features.canvas.history.AddTable
import dbest.features.canvas.history.Command
import dbest.features.canvas.history.json
import dbest.features.sessions.Sessions
import dbest.kernel.adapter.intColumn
import dbest.kernel.adapter.resetStats
import dbest.kernel.adapter.stats
import dbest.kernel.http.router
import dbest.kernel.json.json
import dbest.kernel.json.jsonText
import dbest.kernel.json.parsedJson
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaginationProbe {

    private fun app(): HttpHandler {
        val handler = router(Sessions())
        val created = handler(Request(Method.POST, "/sessions"))
        val sid = parsedJson(created.bodyString()).jsonObject.getValue("sid").jsonPrimitive.content
        return { request ->
            handler(request.uri(request.uri.copy(path = "/sessions/$sid" + request.uri.path)))
        }
    }

    private fun bigTable(rowCount: Int) = MemorySpec(
        "nums",
        listOf(intColumn("id", primaryKey = true)),
        (0 until rowCount).map { mapOf("id" to it) },
    )

    private fun HttpHandler.command(command: Command): Response =
        this(Request(Method.POST, "/commands").body(jsonText(json(command))))

    private fun rowCount(response: Response) =
        parsedJson(response.bodyString()).jsonObject.getValue("rows").jsonArray.size

    private fun ndjsonRowCount(response: Response) =
        response.bodyString().split("\n").count { it.isNotBlank() }

    @Test
    fun `probe pagination on a scan of a 300-row table`() {
        val app = app()
        app.command(AddTable(TableId(0), bigTable(300)))
        app.command(AddNode(NodeId(0), ScanNode(TableId(0), "n"), Position(0.0, 0.0)))

        val paged = app(Request(Method.GET, "/nodes/0/rows?offset=0&limit=200"))
        val unpaged = app(Request(Method.GET, "/nodes/0/rows"))

        println("PROBE paged=${rowCount(paged)} unpaged=${ndjsonRowCount(unpaged)}")
        assertEquals(200, rowCount(paged), "paged request should cap at limit")
        assertEquals(300, ndjsonRowCount(unpaged), "show-all should stream every row, uncapped")
    }

    @Test
    fun `unpaged response pulls a bounded prefix instead of materializing every tuple`() {
        val total = 25_000
        val app = app()
        app.command(AddTable(TableId(0), bigTable(total)))
        app.command(AddNode(NodeId(0), ScanNode(TableId(0), "n"), Position(0.0, 0.0)))
        resetStats()

        val response = app(Request(Method.GET, "/nodes/0/rows"))
        assertTrue(stats().nextCalls < total, "creating the response must not exhaust the cursor")

        assertEquals(1, response.body.stream.read(ByteArray(1)))
        val pulled = stats().nextCalls
        assertTrue(pulled in 1 until total, "one read should fill only the bounded transfer buffer, pulled=$pulled")

        response.close()
    }
}
