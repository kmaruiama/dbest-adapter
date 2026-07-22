package dbest.kernel.adapter.compile

import dbest.kernel.adapter.Aggregate
import dbest.kernel.adapter.Alias
import dbest.kernel.adapter.Collapse
import dbest.kernel.adapter.Conditional
import dbest.kernel.adapter.CrossJoin
import dbest.kernel.adapter.Distinct
import dbest.kernel.adapter.Existence
import dbest.kernel.adapter.Explode
import dbest.kernel.adapter.Filter
import dbest.kernel.adapter.HashIndex
import dbest.kernel.adapter.Join
import dbest.kernel.adapter.Limit
import dbest.kernel.adapter.LogicalOp
import dbest.kernel.adapter.Materialize
import dbest.kernel.adapter.Memoize
import dbest.kernel.adapter.Plan
import dbest.kernel.adapter.Project
import dbest.kernel.adapter.RemoveColumns
import dbest.kernel.adapter.RowNumber
import dbest.kernel.adapter.Scan
import dbest.kernel.adapter.SetOp
import dbest.kernel.adapter.Sort
import dbest.kernel.adapter.gate
import ibd.query.Operation
import ibd.query.binaryop.conditional.Exists
import ibd.query.sourceop.IndexScan
import ibd.query.unaryop.AutoIncrement
import ibd.query.unaryop.Explode as IbdExplode
import ibd.query.unaryop.HashIndex as IbdHashIndex
import ibd.query.unaryop.Limit as IbdLimit
import ibd.query.unaryop.Materialization
import ibd.query.unaryop.Memoize as IbdMemoize
import ibd.query.unaryop.Projection
import ibd.query.unaryop.RemoveColumns as IbdRemoveColumns
import ibd.query.unaryop.SourceRename
import ibd.query.unaryop.SourceRename1
import ibd.query.unaryop.filter.Filter as IbdFilter

internal fun compile(plan: Plan): Operation = gate {
    when (plan) {
        is Scan -> IndexScan(plan.alias, plan.table.table)
        is Filter -> IbdFilter(compile(plan.input), lookupFilter(plan.condition))
        is Project -> Projection(compile(plan.input), plan.columns.toTypedArray())
        is RemoveColumns -> IbdRemoveColumns(compile(plan.input), plan.alias, plan.columns)
        is Sort -> compileSort(plan)
        is Distinct -> compileDistinct(plan)
        is Limit -> IbdLimit(compile(plan.input), plan.count, plan.offset)
        is Alias -> SourceRename(compile(plan.input), plan.from, plan.to)
        is Collapse -> SourceRename1(compile(plan.input), plan.alias)
        is Explode -> IbdExplode(compile(plan.input), plan.column, plan.delimiter)
        is RowNumber -> AutoIncrement(compile(plan.input), plan.alias, plan.column, plan.start)
        is Aggregate -> compileAggregate(plan)
        is Join -> compileJoin(plan)
        is CrossJoin -> compileCross(plan)
        is SetOp -> compileSet(plan)
        is LogicalOp -> compileLogical(plan)
        is Conditional -> compileConditional(plan)
        is Existence -> Exists(compile(plan.left), compile(plan.right), plan.bilateral)
        is Materialize -> Materialization(compile(plan.input))
        is Memoize -> IbdMemoize(compile(plan.input))
        is HashIndex -> IbdHashIndex(compile(plan.input))
    }
}
