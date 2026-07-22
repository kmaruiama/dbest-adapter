package dbest.kernel.adapter.compile

import dbest.kernel.adapter.Conditional
import dbest.kernel.adapter.CrossJoin
import dbest.kernel.adapter.EngineException
import dbest.kernel.adapter.Join
import dbest.kernel.adapter.JoinAlgorithm.HASH
import dbest.kernel.adapter.JoinAlgorithm.MERGE
import dbest.kernel.adapter.JoinAlgorithm.NESTED_LOOP
import dbest.kernel.adapter.JoinType
import dbest.kernel.adapter.LogicalKind
import dbest.kernel.adapter.LogicalOp
import dbest.kernel.adapter.SetKind
import dbest.kernel.adapter.SetOp
import dbest.kernel.adapter.schema
import ibd.query.Operation
import ibd.query.binaryop.conditional.LogicalAnd
import ibd.query.binaryop.conditional.LogicalIf
import ibd.query.binaryop.conditional.LogicalOr
import ibd.query.binaryop.conditional.LogicalXor
import ibd.query.binaryop.join.CrossJoin as IbdCrossJoin
import ibd.query.binaryop.join.HashInnerJoin
import ibd.query.binaryop.join.JoinPredicate
import ibd.query.binaryop.join.MergeJoin
import ibd.query.binaryop.join.NestedLoopJoin
import ibd.query.binaryop.join.anti.HashLeftAntiJoin
import ibd.query.binaryop.join.anti.HashRightAntiJoin
import ibd.query.binaryop.join.anti.MergeLeftAntiJoin
import ibd.query.binaryop.join.anti.MergeRightAntiJoin
import ibd.query.binaryop.join.anti.NestedLoopLeftAntiJoin
import ibd.query.binaryop.join.outer.HashFullOuterJoin
import ibd.query.binaryop.join.outer.HashLeftJoin
import ibd.query.binaryop.join.outer.HashRightJoin
import ibd.query.binaryop.join.outer.MergeFullOuterJoin
import ibd.query.binaryop.join.outer.MergeLeftOuterJoin
import ibd.query.binaryop.join.outer.MergeRightOuterJoin
import ibd.query.binaryop.join.outer.NestedLoopLeftJoin
import ibd.query.binaryop.join.outer.NestedLoopRightJoin
import ibd.query.binaryop.join.semi.HashLeftSemiJoin
import ibd.query.binaryop.join.semi.HashRightSemiJoin
import ibd.query.binaryop.join.semi.MergeLeftSemiJoin
import ibd.query.binaryop.join.semi.MergeRightSemiJoin
import ibd.query.binaryop.join.semi.NestedLoopSemiJoin
import ibd.query.binaryop.set.Append
import ibd.query.binaryop.set.Difference
import ibd.query.binaryop.set.HashDifference
import ibd.query.binaryop.set.HashIntersection
import ibd.query.binaryop.set.HashUnion
import ibd.query.binaryop.set.Intersection
import ibd.query.binaryop.set.Union

internal fun compileJoin(plan: Join): Operation {
    val left: Operation = compile(plan.left)
    val right: Operation = compile(plan.right)
    val predicate: JoinPredicate = JoinPredicate()
    for (term in plan.on) {
        predicate.addTerm(term.left.source, term.left.column, term.right.source, term.right.column)
    }
    return when (plan.type) {
        JoinType.INNER -> when (plan.algorithm) {
            NESTED_LOOP -> NestedLoopJoin(left, right, predicate)
            HASH -> HashInnerJoin(left, right, predicate)
            MERGE -> MergeJoin(left, right, predicate)
        }
        JoinType.LEFT -> when (plan.algorithm) {
            NESTED_LOOP -> NestedLoopLeftJoin(left, right, predicate)
            HASH -> HashLeftJoin(left, right, predicate)
            MERGE -> MergeLeftOuterJoin(left, right, predicate)
        }
        JoinType.RIGHT -> when (plan.algorithm) {
            NESTED_LOOP -> NestedLoopRightJoin(left, right, predicate)
            HASH -> HashRightJoin(left, right, predicate)
            MERGE -> MergeRightOuterJoin(left, right, predicate)
        }
        JoinType.FULL -> when (plan.algorithm) {
            NESTED_LOOP -> unsupportedJoin(plan)
            HASH -> HashFullOuterJoin(left, right, predicate)
            MERGE -> MergeFullOuterJoin(left, right, predicate)
        }
        JoinType.LEFT_SEMI -> when (plan.algorithm) {
            NESTED_LOOP -> NestedLoopSemiJoin(left, right, predicate)
            HASH -> HashLeftSemiJoin(left, right, predicate)
            MERGE -> MergeLeftSemiJoin(left, right, predicate)
        }
        JoinType.RIGHT_SEMI -> when (plan.algorithm) {
            NESTED_LOOP -> unsupportedJoin(plan)
            HASH -> HashRightSemiJoin(left, right, predicate)
            MERGE -> MergeRightSemiJoin(left, right, predicate)
        }
        JoinType.LEFT_ANTI -> when (plan.algorithm) {
            NESTED_LOOP -> NestedLoopLeftAntiJoin(left, right, predicate)
            HASH -> HashLeftAntiJoin(left, right, predicate)
            MERGE -> MergeLeftAntiJoin(left, right, predicate)
        }
        JoinType.RIGHT_ANTI -> when (plan.algorithm) {
            NESTED_LOOP -> unsupportedJoin(plan)
            HASH -> HashRightAntiJoin(left, right, predicate)
            MERGE -> MergeRightAntiJoin(left, right, predicate)
        }
    }
}

private fun unsupportedJoin(plan: Join): Nothing =
    throw EngineException.PlanError("join nao implementado")

internal fun compileCross(plan: CrossJoin): Operation =
    IbdCrossJoin(compile(plan.left), compile(plan.right))

internal fun compileSet(plan: SetOp): Operation {
    val leftSchema = schema(plan.left)
    val rightSchema = schema(plan.right)
    if (leftSchema.size != rightSchema.size) {
        throw EngineException.PlanError(
            "os dois lados de ${plan.alias} precisam ter o mesmo numero de colunas " +
                "(esquerda tem ${leftSchema.size}, direita tem ${rightSchema.size})",
        )
    }
    val left: Operation = compile(plan.left)
    val right: Operation = compile(plan.right)
    val op: Operation = when (plan.kind) {
        SetKind.UNION ->     if (plan.hashed) HashUnion(left, right)
                             else Union(left, right)

        SetKind.INTERSECT -> if (plan.hashed) HashIntersection(left, right)
                             else Intersection(left, right)

        SetKind.EXCEPT ->    if (plan.hashed) HashDifference(left, right)
                             else Difference(left, right)

        SetKind.APPEND -> Append(left, right)
    }
    val inheritedAlias = leftSchema.map { it.source }.distinct().singleOrNull()
    op.dataSourceAlias = inheritedAlias ?: plan.alias
    return op
}

internal fun compileLogical(plan: LogicalOp): Operation {
    val left: Operation = compile(plan.left)
    val right: Operation = compile(plan.right)
    return when (plan.kind) {
        LogicalKind.AND -> LogicalAnd(left, right)
        LogicalKind.OR -> LogicalOr(left, right)
        LogicalKind.XOR -> LogicalXor(left, right)
    }
}

internal fun compileConditional(plan: Conditional): Operation =
    LogicalIf(compile(plan.left), compile(plan.right), lookupFilter(plan.condition))
