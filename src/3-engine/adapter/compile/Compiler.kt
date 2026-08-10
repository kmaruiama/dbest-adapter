// shell imperativo: o dispatch do compilador — os unicos arquivos que importam
// ibd.* sao os tres deste diretorio.
package dbest.adapter.compile

import dbest.adapter.Aggregate
import dbest.adapter.Alias
import dbest.adapter.Collapse
import dbest.adapter.Conditional
import dbest.adapter.CrossJoin
import dbest.adapter.Distinct
import dbest.adapter.Existence
import dbest.adapter.Explode
import dbest.adapter.Filter
import dbest.adapter.HashIndex
import dbest.adapter.Join
import dbest.adapter.Limit
import dbest.adapter.LogicalOp
import dbest.adapter.Materialize
import dbest.adapter.Memoize
import dbest.adapter.Plan
import dbest.adapter.Project
import dbest.adapter.RemoveColumns
import dbest.adapter.RowNumber
import dbest.adapter.Scan
import dbest.adapter.SetOp
import dbest.adapter.Sort
import dbest.adapter.gate
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
