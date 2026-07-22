// shell imperativo: operadores unarios — sort, distinct, agregacao, e a
// traducao de condicao -> filtro da engine.
package dbest.adapter.compile

import dbest.adapter.Agg
import dbest.adapter.AggFunction
import dbest.adapter.Aggregate
import dbest.adapter.And
import dbest.adapter.ColumnRef
import dbest.adapter.CompareOp
import dbest.adapter.Comparison
import dbest.adapter.Condition
import dbest.adapter.Distinct
import dbest.adapter.IsNotNull
import dbest.adapter.IsNull
import dbest.adapter.Or
import dbest.adapter.QualifiedCol
import dbest.adapter.Sort
import dbest.misc.mapCollection
import ibd.query.ColumnDescriptor
import ibd.query.Operation
import ibd.query.lookup.ColumnElement
import ibd.query.lookup.CompositeLookupFilter
import ibd.query.lookup.Element
import ibd.query.lookup.LiteralElement
import ibd.query.lookup.LookupFilter
import ibd.query.lookup.SingleColumnLookupFilter
import ibd.query.unaryop.DuplicateRemoval
import ibd.query.unaryop.HashDuplicateRemoval
import ibd.query.unaryop.aggregation.Aggregation
import ibd.query.unaryop.aggregation.AggregationType
import ibd.query.unaryop.aggregation.AllAggregation
import ibd.query.unaryop.aggregation.HashAggregation
import ibd.query.unaryop.sort.Sort as IbdSort
import ibd.table.ComparisonTypes

internal fun compileSort(plan: Sort): Operation {
    val columns: Array<String> = Array(plan.keys.size) { i -> plan.keys[i].column }
    val ascending: BooleanArray = BooleanArray(plan.keys.size) { i -> plan.keys[i].ascending }
    return IbdSort(compile(plan.input), columns, ascending)
}

internal fun compileDistinct(plan: Distinct): Operation =
    if (plan.hashed) HashDuplicateRemoval(compile(plan.input)) else DuplicateRemoval(compile(plan.input))

// A agregacao baseada em ordenacao deliberadamente NAO ordena sua entrada: como o
// {} Group da GUI antiga, o aluno posiciona o Sort — entrada nao ordenada produz
// grupos fragmentados, que eh a licao. (isOrdered=false esta quebrado na engine,
// ver BUGS.md #1, entao a escolha eh feita aqui, nao delegada para a engine.)
internal fun compileAggregate(plan: Aggregate): Operation {
    val input: Operation = compile(plan.input)
    val by: QualifiedCol = plan.by
        ?: return AllAggregation(input, plan.alias, aggregationTypes(plan.aggregates))
    return if (plan.hashed) HashAggregation(input, plan.alias, by.source, by.column, aggregationTypes(plan.aggregates), true)
    else Aggregation(input, plan.alias, by.source, by.column, aggregationTypes(plan.aggregates), true)
}

private fun aggregationTypes(aggregates: List<Agg>): List<AggregationType> =
    mapCollection(aggregates, ::aggregationType)

private fun aggregationType(aggregate: Agg): AggregationType =
    AggregationType(aggregate.column, engineAggType(aggregate.function))

private fun engineAggType(function: AggFunction): Int = when (function) {
    AggFunction.MAX -> AggregationType.MAX
    AggFunction.MIN -> AggregationType.MIN
    AggFunction.COUNT -> AggregationType.COUNT
    AggFunction.AVG -> AggregationType.AVG
    AggFunction.SUM -> AggregationType.SUM
    AggFunction.FIRST -> AggregationType.FIRST
    AggFunction.LAST -> AggregationType.LAST
    AggFunction.COUNT_ALL -> AggregationType.COUNT_ALL
    AggFunction.COUNT_NULL -> AggregationType.COUNT_NULL
}

internal fun lookupFilter(condition: Condition): LookupFilter = when (condition) {
    is Comparison -> SingleColumnLookupFilter(element(condition.left), engineCompareOp(condition.op), element(condition.right))
    is IsNull -> SingleColumnLookupFilter(element(condition.column), ComparisonTypes.IS_NULL, LiteralElement(null))
    is IsNotNull -> SingleColumnLookupFilter(element(condition.column), ComparisonTypes.IS_NOT_NULL, LiteralElement(null))
    is And -> composite(CompositeLookupFilter.AND, condition.conditions)
    is Or -> composite(CompositeLookupFilter.OR, condition.conditions)
}

private fun composite(connector: Int, conditions: List<Condition>): CompositeLookupFilter {
    val filter: CompositeLookupFilter = CompositeLookupFilter(connector)
    for (condition in conditions) {
        filter.addFilter(lookupFilter(condition))
    }
    return filter
}

private fun element(value: Any): Element = when {
    value !is ColumnRef -> LiteralElement(value as Comparable<*>)
    value.source == null -> ColumnElement(ColumnDescriptor(value.name))
    else -> ColumnElement(ColumnDescriptor(value.source, value.name))
}

private fun engineCompareOp(op: CompareOp): Int = when (op) {
    CompareOp.EQ -> ComparisonTypes.EQUAL
    CompareOp.NEQ -> ComparisonTypes.DIFF
    CompareOp.GT -> ComparisonTypes.GREATER_THAN
    CompareOp.GTE -> ComparisonTypes.GREATER_EQUAL_THAN
    CompareOp.LT -> ComparisonTypes.LOWER_THAN
    CompareOp.LTE -> ComparisonTypes.LOWER_EQUAL_THAN
}
