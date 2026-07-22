package dbest.features.canvas.query

import dbest.features.canvas.graph.json
import dbest.kernel.json.json
import dbest.kernel.json.obj
import kotlinx.serialization.json.JsonElement

fun json(problem: Problem): JsonElement =
    obj("node" to json(problem.node), "message" to json(problem.message))
