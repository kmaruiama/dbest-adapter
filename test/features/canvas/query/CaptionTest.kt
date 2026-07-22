package dbest.features.canvas.query

import dbest.features.canvas.graph.AggNode
import dbest.features.canvas.graph.AliasNode
import dbest.features.canvas.graph.CollapseNode
import dbest.features.canvas.graph.CrossNode
import dbest.features.canvas.graph.DistinctNode
import dbest.features.canvas.graph.ExistsNode
import dbest.features.canvas.graph.ExplodeNode
import dbest.features.canvas.graph.FilterNode
import dbest.features.canvas.graph.HashIndexNode
import dbest.features.canvas.graph.JoinNode
import dbest.features.canvas.graph.LimitNode
import dbest.features.canvas.graph.LogicalOpNode
import dbest.features.canvas.graph.MaterializeNode
import dbest.features.canvas.graph.MemoizeNode
import dbest.features.canvas.graph.MemorySpec
import dbest.features.canvas.graph.Node
import dbest.features.canvas.graph.NodeId
import dbest.features.canvas.graph.ProjectNode
import dbest.features.canvas.graph.RemoveColumnsNode
import dbest.features.canvas.graph.RowNumberNode
import dbest.features.canvas.graph.ScanNode
import dbest.features.canvas.graph.Session
import dbest.features.canvas.graph.SetOpNode
import dbest.features.canvas.graph.SortNode
import dbest.features.canvas.graph.TableId
import dbest.features.catalog.CATALOG
import dbest.features.catalog.catalogKinds
import dbest.features.catalog.sampleOf
import dbest.kernel.adapter.Agg
import dbest.kernel.adapter.AggFunction
import dbest.kernel.adapter.ColumnRef
import dbest.kernel.adapter.CompareOp
import dbest.kernel.adapter.Comparison
import dbest.kernel.adapter.IsNotNull
import dbest.kernel.adapter.IsNull
import dbest.kernel.adapter.JoinAlgorithm
import dbest.kernel.adapter.JoinTerm
import dbest.kernel.adapter.JoinType
import dbest.kernel.adapter.LogicalKind
import dbest.kernel.adapter.QualifiedCol
import dbest.kernel.adapter.SetKind
import dbest.kernel.adapter.SortKey
import dbest.kernel.adapter.and
import dbest.kernel.adapter.joinClass
import dbest.kernel.adapter.or
import dbest.kernel.util.isEmpty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CaptionTest {

    private val UNSUPPORTED: Set<JoinType> =
        setOf(JoinType.FULL, JoinType.RIGHT_SEMI, JoinType.RIGHT_ANTI)

    private fun sessionOf(vararg nodes: Node): Session {
        val table = MemorySpec("orders", emptyList())
        val byId = HashMap<NodeId, Node>()
        for ((index, node) in nodes.withIndex()) {
            byId[NodeId(index)] = node
        }
        return Session(tables = mapOf(TableId(0) to table), nodes = byId)
    }

    private fun expression(node: Node): String = expressionOf(sessionOf(node), node)

    private fun filter(op: CompareOp, right: Any): String =
        expression(FilterNode(Comparison(ColumnRef("u", "age"), op, right)))

    @Test
    fun `every operator the palette serves resolves to an engine class`() {
        for (kind in catalogKinds()) {
            val engineClass = engineClassOf(sampleOf(kind))
            assertNotNull(engineClass, "$kind nao tem classe de engine")
            assertTrue(!isEmpty(engineClass), "$kind tem classe de engine vazia")
        }
    }

    @Test
    fun `scan resolves to an engine class even though the palette has no chip for it`() {
        assertEquals("IndexScan", engineClassOf(ScanNode(TableId(0), "o")))
    }

    @Test
    fun `only the join combinations the engine refuses are without a class`() {
        for (type in JoinType.entries) {
            for (algorithm in JoinAlgorithm.entries) {
                val engineClass = joinClass(type, algorithm)
                val refused = algorithm == JoinAlgorithm.NESTED_LOOP && UNSUPPORTED.contains(type)
                if (refused) {
                    assertNull(engineClass, "$type/$algorithm nao deveria ter classe")
                } else {
                    assertNotNull(engineClass, "$type/$algorithm deveria ter classe")
                }
            }
        }
    }

    @Test
    fun `a join picks its class from its own type and algorithm`() {
        assertEquals("NestedLoopJoin", engineClassOf(join(JoinType.INNER, JoinAlgorithm.NESTED_LOOP)))
        assertEquals("MergeJoin", engineClassOf(join(JoinType.INNER, JoinAlgorithm.MERGE)))
        assertEquals("HashLeftJoin", engineClassOf(join(JoinType.LEFT, JoinAlgorithm.HASH)))
        assertEquals("MergeFullOuterJoin", engineClassOf(join(JoinType.FULL, JoinAlgorithm.MERGE)))
        assertNull(engineClassOf(join(JoinType.FULL, JoinAlgorithm.NESTED_LOOP)))
    }

    private fun join(type: JoinType, algorithm: JoinAlgorithm): Node =
        JoinNode(listOf(JoinTerm(QualifiedCol("m1", "id"), QualifiedCol("m2", "id"))), type, algorithm)

    @Test
    fun `hashing and grouping pick the class the compiler would`() {
        assertEquals("HashDuplicateRemoval", engineClassOf(DistinctNode(true)))
        assertEquals("DuplicateRemoval", engineClassOf(DistinctNode(false)))

        val counted = listOf(Agg("id", AggFunction.COUNT))
        assertEquals("AllAggregation", engineClassOf(AggNode("a", null, counted, true)))
        assertEquals("HashAggregation", engineClassOf(AggNode("a", QualifiedCol("u", "dept"), counted, true)))
        assertEquals("Aggregation", engineClassOf(AggNode("a", QualifiedCol("u", "dept"), counted, false)))

        assertEquals("HashUnion", engineClassOf(SetOpNode(SetKind.UNION, true)))
        assertEquals("Union", engineClassOf(SetOpNode(SetKind.UNION, false)))
        assertEquals("Append", engineClassOf(SetOpNode(SetKind.APPEND, false)))
        assertEquals("LogicalXor", engineClassOf(LogicalOpNode(LogicalKind.XOR)))
    }

    @Test
    fun `a comparison keeps its own operator`() {
        assertEquals("u.age = 30", filter(CompareOp.EQ, 30))
        assertEquals("u.age ≠ 30", filter(CompareOp.NEQ, 30))
        assertEquals("u.age > 30", filter(CompareOp.GT, 30))
        assertEquals("u.age ≥ 30", filter(CompareOp.GTE, 30))
        assertEquals("u.age < 30", filter(CompareOp.LT, 30))
        assertEquals("u.age ≤ 30", filter(CompareOp.LTE, 30))
    }

    @Test
    fun `literals read as themselves and column comparisons stay unquoted`() {
        assertEquals("u.age = 'Ana'", filter(CompareOp.EQ, "Ana"))
        assertEquals("u.age = true", filter(CompareOp.EQ, true))
        assertEquals("u.age = 1.5", filter(CompareOp.EQ, 1.5))
        assertEquals("u.age = o.age", filter(CompareOp.EQ, ColumnRef("o", "age")))
        assertEquals("u.age = age", filter(CompareOp.EQ, ColumnRef(null, "age")))
    }

    @Test
    fun `null checks and nested boolean trees render`() {
        assertEquals("u.age IS NULL", expression(FilterNode(IsNull(ColumnRef("u", "age")))))
        assertEquals("u.age IS NOT NULL", expression(FilterNode(IsNotNull(ColumnRef("u", "age")))))

        val left = Comparison(ColumnRef("u", "age"), CompareOp.GT, 30)
        val right = Comparison(ColumnRef("u", "name"), CompareOp.EQ, "Ana")
        assertEquals("u.age > 30 AND u.name = 'Ana'", expression(FilterNode(and(left, right))))
        assertEquals(
            "u.age > 30 OR (u.age > 30 AND u.name = 'Ana')",
            expression(FilterNode(or(left, and(left, right)))),
        )
    }

    @Test
    fun `every node kind renders the expression a reader needs`() {
        assertEquals("orders as o", expression(ScanNode(TableId(0), "o")))
        assertEquals("m1.id = m2.id", expression(join(JoinType.INNER, JoinAlgorithm.HASH)))
        assertEquals("id, name", expression(ProjectNode(listOf("id", "name"))))
        assertEquals("id, name", expression(RemoveColumnsNode(listOf("id", "name"))))
        assertEquals("age ASC, name DESC", expression(SortNode(listOf(SortKey("age", true), SortKey("name", false)))))
        assertEquals("10", expression(LimitNode(10)))
        assertEquals("10, 20", expression(LimitNode(10, 20)))
        assertEquals("u → users", expression(AliasNode("u", "users")))
        assertEquals("all", expression(CollapseNode("all")))
        assertEquals("tags", expression(ExplodeNode("tags")))
        assertEquals("n from 1", expression(RowNumberNode("n", "id")))
        assertEquals("UNION", expression(SetOpNode(SetKind.UNION, true)))
        assertEquals("AND", expression(LogicalOpNode(LogicalKind.AND)))

        val counted = listOf(Agg("id", AggFunction.COUNT))
        assertEquals("count(id)", expression(AggNode("a", null, counted, true)))
        assertEquals("u.dept: count(id)", expression(AggNode("a", QualifiedCol("u", "dept"), counted, true)))
    }

    @Test
    fun `operators with nothing to configure carry no expression`() {
        assertEquals("", expression(MaterializeNode))
        assertEquals("", expression(MemoizeNode))
        assertEquals("", expression(HashIndexNode))
        assertEquals("", expression(CrossNode))
        assertEquals("", expression(DistinctNode(true)))
        assertEquals("", expression(ExistsNode(false)))
    }

    @Test
    fun `an unconfigured palette chip renders its placeholders instead of failing`() {
        for (chip in CATALOG) {
            expressionOf(sessionOf(chip.template), chip.template)
        }
    }
}
