package dbest.features.canvas.graph

import dbest.kernel.adapter.Agg
import dbest.kernel.adapter.Condition
import dbest.kernel.adapter.JoinAlgorithm
import dbest.kernel.adapter.JoinTerm
import dbest.kernel.adapter.JoinType
import dbest.kernel.adapter.LogicalKind
import dbest.kernel.adapter.QualifiedCol
import dbest.kernel.adapter.SetKind
import dbest.kernel.adapter.SortKey
import dbest.kernel.util.isBlank
import dbest.kernel.util.isEmpty

sealed interface Node

sealed interface SourceNode : Node

sealed interface UnaryNode : Node

sealed interface BinaryNode : Node

fun inputPorts(node: Node): Set<Port> = when (node) {
    is SourceNode -> emptySet()
    is UnaryNode -> setOf(Port.ONLY)
    is BinaryNode -> setOf(Port.LEFT, Port.RIGHT)
}

fun operatorKind(node: Node): String = when (node) {
    is ScanNode -> "scan"
    is FilterNode -> "filter"
    is ProjectNode -> "project"
    is SortNode -> "sort"
    is DistinctNode -> "distinct"
    is LimitNode -> "limit"
    is AliasNode -> "alias"
    is CollapseNode -> "collapse"
    is ExplodeNode -> "explode"
    is RowNumberNode -> "rowNumber"
    is AggNode -> "agg"
    is RemoveColumnsNode -> "removeColumns"
    is MaterializeNode -> "materialize"
    is MemoizeNode -> "memoize"
    is HashIndexNode -> "hashIndex"
    is JoinNode -> "join"
    is CrossNode -> "cross"
    is SetOpNode -> "setOp"
    is LogicalOpNode -> "logicalOp"
    is ExistsNode -> "exists"
}

data class ScanNode(val table: TableId, val alias: String) : SourceNode {
    init {
        require(!isEmpty(alias), { "O alias nao pode ser uma string vazia" })
    }
}

data class FilterNode(val condition: Condition) : UnaryNode

data class ProjectNode(val columns: List<String>) : UnaryNode {
    init {
        require(!isEmpty(columns), { "Eh necessario selecionar no minimo uma coluna para projecao" })
    }
}

data class SortNode(val keys: List<SortKey>) : UnaryNode {
    init {
        require(!isEmpty(keys), { "Sort precisa de pelo menos um parametro" })
    }
}

data class DistinctNode(val hashed: Boolean = true) : UnaryNode

data class LimitNode(val count: Int, val offset: Int = 0) : UnaryNode {
    init {
        require(count > 0, { "Limit precisa ser positivo" })
        require(offset >= 0, { "Offset nao pode ser negativo" })
    }
}

data class AliasNode(val from: String, val to: String) : UnaryNode {
    init {
        require(!isBlank(from) && !isBlank(to), { "Alias precisa do alias atual e do novo alias da fonte" })
    }
}

data class CollapseNode(val alias: String) : UnaryNode {
    init {
        require(!isBlank(alias), { "Collapse precisa de um alias para a fonte unificada" })
    }
}

data class ExplodeNode(val column: String, val delimiter: String = ",") : UnaryNode {
    init {
        require(!isBlank(column), { "Explode precisa de uma coluna" })
        require(!isEmpty(delimiter), { "Explode precisa de um delimitador" })
    }
}

data class RowNumberNode(val alias: String, val column: String, val start: Int = 1) : UnaryNode {
    init {
        require(!isBlank(alias), { "RowNumber precisa de um alias de fonte para sua saida" })
        require(!isBlank(column), { "RowNumber precisa do nome de uma coluna" })
    }
}

data class AggNode(val alias: String, val by: QualifiedCol?, val aggregates: List<Agg>, val hashed: Boolean = true) : UnaryNode {
    init {
        require(!isBlank(alias), { "Agregacao precisa de um alias de fonte para sua saida" })
        require(!isEmpty(aggregates), { "Agregacao precisa de pelo menos um agregado" })
    }
}

data class RemoveColumnsNode(val columns: List<String>, val alias: String = "Projection") : UnaryNode {
    init {
        require(!isEmpty(columns), { "RemoveColumns precisa de pelo menos uma coluna" })
        require(!isBlank(alias), { "RemoveColumns precisa de um alias para sua saida" })
    }
}

data object MaterializeNode : UnaryNode

data object MemoizeNode : UnaryNode

data object HashIndexNode : UnaryNode

data class JoinNode(val on: List<JoinTerm>, val type: JoinType = JoinType.INNER, val algorithm: JoinAlgorithm = JoinAlgorithm.NESTED_LOOP) : BinaryNode {
    init {
        require(!isEmpty(on), { "Join precisa de pelo menos um termo de igualdade" })
    }
}

data object CrossNode : BinaryNode

data class SetOpNode(val kind: SetKind, val hashed: Boolean = true) : BinaryNode

data class LogicalOpNode(val kind: LogicalKind) : BinaryNode

data class ExistsNode(val bilateral: Boolean = false) : BinaryNode
