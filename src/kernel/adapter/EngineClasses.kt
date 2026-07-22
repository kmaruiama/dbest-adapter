package dbest.kernel.adapter

import ibd.query.binaryop.conditional.Exists
import ibd.query.binaryop.conditional.LogicalAnd
import ibd.query.binaryop.conditional.LogicalOr
import ibd.query.binaryop.conditional.LogicalXor
import ibd.query.binaryop.join.CrossJoin as IbdCrossJoin
import ibd.query.binaryop.join.HashInnerJoin
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
import ibd.query.sourceop.IndexScan
import ibd.query.unaryop.AutoIncrement
import ibd.query.unaryop.DuplicateRemoval
import ibd.query.unaryop.Explode as IbdExplode
import ibd.query.unaryop.HashDuplicateRemoval
import ibd.query.unaryop.HashIndex as IbdHashIndex
import ibd.query.unaryop.Limit as IbdLimit
import ibd.query.unaryop.Materialization
import ibd.query.unaryop.Memoize as IbdMemoize
import ibd.query.unaryop.Projection
import ibd.query.unaryop.RemoveColumns as IbdRemoveColumns
import ibd.query.unaryop.SourceRename
import ibd.query.unaryop.SourceRename1
import ibd.query.unaryop.aggregation.Aggregation
import ibd.query.unaryop.aggregation.AllAggregation
import ibd.query.unaryop.aggregation.HashAggregation
import ibd.query.unaryop.filter.Filter as IbdFilter
import ibd.query.unaryop.sort.Sort as IbdSort

val SCAN_CLASS: String = IndexScan::class.java.simpleName
val FILTER_CLASS: String = IbdFilter::class.java.simpleName
val PROJECT_CLASS: String = Projection::class.java.simpleName
val REMOVE_COLUMNS_CLASS: String = IbdRemoveColumns::class.java.simpleName
val SORT_CLASS: String = IbdSort::class.java.simpleName
val LIMIT_CLASS: String = IbdLimit::class.java.simpleName
val ALIAS_CLASS: String = SourceRename::class.java.simpleName
val COLLAPSE_CLASS: String = SourceRename1::class.java.simpleName
val EXPLODE_CLASS: String = IbdExplode::class.java.simpleName
val ROW_NUMBER_CLASS: String = AutoIncrement::class.java.simpleName
val MATERIALIZE_CLASS: String = Materialization::class.java.simpleName
val MEMOIZE_CLASS: String = IbdMemoize::class.java.simpleName
val HASH_INDEX_CLASS: String = IbdHashIndex::class.java.simpleName
val CROSS_CLASS: String = IbdCrossJoin::class.java.simpleName
val EXISTS_CLASS: String = Exists::class.java.simpleName

fun distinctClass(hashed: Boolean): String =
    if (hashed) HashDuplicateRemoval::class.java.simpleName else DuplicateRemoval::class.java.simpleName

fun aggClass(grouped: Boolean, hashed: Boolean): String = when {
    !grouped -> AllAggregation::class.java.simpleName
    hashed -> HashAggregation::class.java.simpleName
    else -> Aggregation::class.java.simpleName
}

fun setClass(kind: SetKind, hashed: Boolean): String = when (kind) {
    SetKind.UNION -> if (hashed) HashUnion::class.java.simpleName else Union::class.java.simpleName
    SetKind.INTERSECT -> if (hashed) HashIntersection::class.java.simpleName else Intersection::class.java.simpleName
    SetKind.EXCEPT -> if (hashed) HashDifference::class.java.simpleName else Difference::class.java.simpleName
    SetKind.APPEND -> Append::class.java.simpleName
}

fun logicalClass(kind: LogicalKind): String = when (kind) {
    LogicalKind.AND -> LogicalAnd::class.java.simpleName
    LogicalKind.OR -> LogicalOr::class.java.simpleName
    LogicalKind.XOR -> LogicalXor::class.java.simpleName
}

fun joinClass(type: JoinType, algorithm: JoinAlgorithm): String? = when (type) {
    JoinType.INNER -> when (algorithm) {
        JoinAlgorithm.NESTED_LOOP -> NestedLoopJoin::class.java.simpleName
        JoinAlgorithm.HASH -> HashInnerJoin::class.java.simpleName
        JoinAlgorithm.MERGE -> MergeJoin::class.java.simpleName
    }
    JoinType.LEFT -> when (algorithm) {
        JoinAlgorithm.NESTED_LOOP -> NestedLoopLeftJoin::class.java.simpleName
        JoinAlgorithm.HASH -> HashLeftJoin::class.java.simpleName
        JoinAlgorithm.MERGE -> MergeLeftOuterJoin::class.java.simpleName
    }
    JoinType.RIGHT -> when (algorithm) {
        JoinAlgorithm.NESTED_LOOP -> NestedLoopRightJoin::class.java.simpleName
        JoinAlgorithm.HASH -> HashRightJoin::class.java.simpleName
        JoinAlgorithm.MERGE -> MergeRightOuterJoin::class.java.simpleName
    }
    JoinType.FULL -> when (algorithm) {
        JoinAlgorithm.NESTED_LOOP -> null
        JoinAlgorithm.HASH -> HashFullOuterJoin::class.java.simpleName
        JoinAlgorithm.MERGE -> MergeFullOuterJoin::class.java.simpleName
    }
    JoinType.LEFT_SEMI -> when (algorithm) {
        JoinAlgorithm.NESTED_LOOP -> NestedLoopSemiJoin::class.java.simpleName
        JoinAlgorithm.HASH -> HashLeftSemiJoin::class.java.simpleName
        JoinAlgorithm.MERGE -> MergeLeftSemiJoin::class.java.simpleName
    }
    JoinType.RIGHT_SEMI -> when (algorithm) {
        JoinAlgorithm.NESTED_LOOP -> null
        JoinAlgorithm.HASH -> HashRightSemiJoin::class.java.simpleName
        JoinAlgorithm.MERGE -> MergeRightSemiJoin::class.java.simpleName
    }
    JoinType.LEFT_ANTI -> when (algorithm) {
        JoinAlgorithm.NESTED_LOOP -> NestedLoopLeftAntiJoin::class.java.simpleName
        JoinAlgorithm.HASH -> HashLeftAntiJoin::class.java.simpleName
        JoinAlgorithm.MERGE -> MergeLeftAntiJoin::class.java.simpleName
    }
    JoinType.RIGHT_ANTI -> when (algorithm) {
        JoinAlgorithm.NESTED_LOOP -> null
        JoinAlgorithm.HASH -> HashRightAntiJoin::class.java.simpleName
        JoinAlgorithm.MERGE -> MergeRightAntiJoin::class.java.simpleName
    }
}
