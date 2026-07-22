package dbest.features.canvas.history

import dbest.features.canvas.graph.NodeId
import dbest.features.canvas.graph.TableId
import dbest.features.canvas.graph.edgeOf
import dbest.features.canvas.graph.json
import dbest.features.canvas.graph.nodeOf
import dbest.features.canvas.graph.positionOf
import dbest.features.canvas.graph.tableSpecOf
import dbest.kernel.json.elementsOf
import dbest.kernel.json.field
import dbest.kernel.json.int
import dbest.kernel.json.json
import dbest.kernel.json.obj
import dbest.kernel.json.objOf
import dbest.kernel.json.tag
import dbest.kernel.json.wireError
import dbest.kernel.util.mapCollection
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

fun json(command: Command): JsonElement = when (command) {
    is AddTable -> obj("@type" to json("addTable"), "id" to json(command.id), "spec" to json(command.spec))
    is RemoveTable -> obj("@type" to json("removeTable"), "id" to json(command.id))
    is AddNode -> obj(
        "@type" to json("addNode"),
        "id" to json(command.id),
        "node" to json(command.node),
        "at" to json(command.at),
    )
    is SetNode -> obj("@type" to json("setNode"), "id" to json(command.id), "node" to json(command.node))
    is RemoveNode -> obj("@type" to json("removeNode"), "id" to json(command.id))
    is Connect -> obj("@type" to json("connect"), "edge" to json(command.edge))
    is Disconnect -> obj("@type" to json("disconnect"), "edge" to json(command.edge))
    is Move -> obj("@type" to json("move"), "id" to json(command.id), "to" to json(command.to))
    is Batch -> obj("@type" to json("batch"), "commands" to JsonArray(mapCollection(command.commands, ::json)))
}

fun commandOf(element: JsonElement): Command {
    val fields = objOf(element)
    return when (val tag = fields.tag()) {
        "addTable" -> AddTable(TableId(fields.int("id")), tableSpecOf(fields.field("spec")))
        "removeTable" -> RemoveTable(TableId(fields.int("id")))
        "addNode" -> AddNode(NodeId(fields.int("id")), nodeOf(fields.field("node")), positionOf(fields.field("at")))
        "setNode" -> SetNode(NodeId(fields.int("id")), nodeOf(fields.field("node")))
        "removeNode" -> RemoveNode(NodeId(fields.int("id")))
        "connect" -> Connect(edgeOf(fields.field("edge")))
        "disconnect" -> Disconnect(edgeOf(fields.field("edge")))
        "move" -> Move(NodeId(fields.int("id")), positionOf(fields.field("to")))
        "batch" -> Batch(mapCollection(elementsOf(fields.field("commands")), ::commandOf))
        else -> wireError("comando desconhecido '$tag'")
    }
}
