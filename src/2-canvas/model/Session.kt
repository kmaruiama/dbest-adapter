// NAMESPACE PURO, NADA AQUI EH SHELL

package dbest.model

data class Session(
    val tables: Map<TableId, TableSpec> = emptyMap(),
    val nodes: Map<NodeId, Node> = emptyMap(),
    val edges: Set<Edge> = emptySet(),
    val layout: Map<NodeId, Position> = emptyMap(),
)

/*

  Por que usar value classes ao inves de Int sem uma classe?

  o objetivo e forcar uma deteccao mais estrita do compilador.
  Como ambos, tables e nodes (operadores), usam integers, e facil
  cometer erros como passar um NodeId onde precisavamos de um TableId

  ao usar value classes, o compilador impede essa troca acidental.
  O @JvmInline aqui serve para que esse wrapper desaparece em runtime,
  tornando-se um int primitivo novamente.

 */

@JvmInline
value class NodeId(val value: Int)

@JvmInline
value class TableId(val value: Int)

data class Position(val x: Double, val y: Double)

enum class Port { ONLY, LEFT, RIGHT }

data class Edge(val from: NodeId, val to: NodeId, val port: Port)