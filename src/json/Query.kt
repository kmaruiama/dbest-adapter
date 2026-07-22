package dbest.json

import dbest.model.Problem
import kotlinx.serialization.json.JsonElement

fun json(problem: Problem): JsonElement =
    obj("node" to json(problem.node), "message" to json(problem.message))
