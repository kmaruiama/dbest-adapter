# Task 1 — Conditional / boolean operators (IF, AND, OR, XOR; + REFERENCE, CONDITION)

**Read `plans/README.md` first** (architecture + conventions + blast radius). This is a pure
extension of the existing operator pipeline; no new module.

## Goal

Add the legacy palette's boolean-operator family. Engine classes already exist:

| Legacy op | Engine class (`ibd.query…`) | Constructor |
|-----------|------------------------------|-------------|
| AND | `binaryop.conditional.LogicalAnd` | `(Operation left, Operation right)` |
| OR  | `binaryop.conditional.LogicalOr`  | `(Operation left, Operation right)` |
| XOR | `binaryop.conditional.LogicalXor` | `(Operation left, Operation right)` |
| IF  | `binaryop.conditional.LogicalIf`  | `(Operation left, Operation right, LookupFilter filter)` |
| CONDITION | `unaryop.filter.Condition` | `(LookupFilter filter)` |
| REFERENCE | `unaryop.Reference` | `(String[] columns)` |

## Recommended scope (be disciplined — don't copy the legacy 1:1)

Implement **AND / OR / XOR / IF** as first-class operators. For the other two, prefer the
cleaner model over palette parity:

- **REFERENCE — defer / drop.** Its purpose was reusing a subtree by name. The new canvas is
  already a DAG (a node may fan out to several consumers — `Connect` only forbids duplicate
  edges, double-connected input ports, and cycles), so reuse is native. Only implement if the
  frontend genuinely needs a named nullary source; if so it is a new `SourceNode`, not a unary.
- **CONDITION — fold into IF.** In the legacy it is a standalone predicate feeding IF. Here IF
  already carries its own `Condition` inline (below), so a separate CONDITION node is redundant.
  Skip unless the frontend design requires a free-standing predicate cell.

State this decision in the PR description so it's a choice, not an omission.

## Design (mirror the existing `SetOp` / `Existence` precedent)

`LogicalAnd/Or/Xor` differ only by kind → one operator with a kind enum, exactly like
`SetOp(kind: SetKind)`. `IF` carries a condition → its own type, and it **reuses the existing
`Condition` ADT + `lookupFilter()` translation**, so no new filter plumbing.

### Adapter — `src/3-engine/adapter/Plan.kt`
```kotlin
// boolean/conditional --------------------------------------------------------
enum class LogicalKind { AND, OR, XOR }

data class LogicalOp(val left: Plan, val right: Plan, val kind: LogicalKind) : Plan

fun logicalAnd(left: Plan, right: Plan): Plan = LogicalOp(left, right, LogicalKind.AND)
fun logicalOr(left: Plan, right: Plan): Plan  = LogicalOp(left, right, LogicalKind.OR)
fun logicalXor(left: Plan, right: Plan): Plan = LogicalOp(left, right, LogicalKind.XOR)

// IF: emite tuplas de um lado ou do outro conforme a condicao (confirmar semantica
// left/right contra LogicalIf antes de finalizar)
data class Conditional(val left: Plan, val right: Plan, val condition: Condition) : Plan

fun conditional(left: Plan, right: Plan, condition: Condition): Plan =
    Conditional(left, right, condition)
```
Factory names avoid the existing `and()`/`or()` **condition** combinators in `Conditions.kt`.

### Adapter — `src/3-engine/adapter/compile/Binary.kt`
Add imports for `LogicalAnd/LogicalOr/LogicalXor/LogicalIf`, and:
```kotlin
internal fun compileLogical(plan: LogicalOp): Operation {
    val left = compile(plan.left); val right = compile(plan.right)
    return when (plan.kind) {
        LogicalKind.AND -> LogicalAnd(left, right)
        LogicalKind.OR  -> LogicalOr(left, right)
        LogicalKind.XOR -> LogicalXor(left, right)
    }
}

internal fun compileConditional(plan: Conditional): Operation =
    LogicalIf(compile(plan.left), compile(plan.right), lookupFilter(plan.condition))
```
`lookupFilter` lives in `Unary.kt` (same `compile` package) — reuse it directly.

### Adapter — `src/3-engine/adapter/compile/Compiler.kt`
Two new `when` branches: `is LogicalOp -> compileLogical(plan)`,
`is Conditional -> compileConditional(plan)`.

### Model — `src/2-canvas/model/Nodes.kt`
```kotlin
data class LogicalOpNode(val kind: LogicalKind) : BinaryNode
data class ConditionalNode(val condition: Condition) : BinaryNode
```
Add `operatorKind` branches: `"logicalOp"`, `"conditional"`. (No `inputPorts` change — both are
`BinaryNode`, so they get LEFT/RIGHT for free.)

### Model — `src/2-canvas/model/Query.kt`
```kotlin
is LogicalOpNode -> when (node.kind) {
    LogicalKind.AND -> logicalAnd(input(Port.LEFT), input(Port.RIGHT))
    LogicalKind.OR  -> logicalOr(input(Port.LEFT), input(Port.RIGHT))
    LogicalKind.XOR -> logicalXor(input(Port.LEFT), input(Port.RIGHT))
}
is ConditionalNode -> conditional(input(Port.LEFT), input(Port.RIGHT), node.condition)
```

### JSON — `src/json/Nodes.kt`
Encoder:
```kotlin
is LogicalOpNode  -> obj("@type" to json(operatorKind(node)), "kind" to json(node.kind.name))
is ConditionalNode -> obj("@type" to json(operatorKind(node)), "condition" to json(node.condition))
```
Decoder (`nodeOf`): `"logicalOp" -> LogicalOpNode(fields.enum<LogicalKind>("kind"))`,
`"conditional" -> ConditionalNode(conditionOf(fields.field("condition")))`.
`json(Condition)` / `conditionOf` already exist in `AdapterCodec.kt` — reuse.

## Tests
- `test/adapter/SmokeTest.kt`: build two small memory tables and assert AND/OR/XOR/IF row
  results. **First** write a throwaway probe to pin the exact semantics of each engine class
  (what does `LogicalAnd` emit? intersection-of-existence? row-level?) and of `LogicalIf`'s
  left/right — then assert against confirmed behaviour, not assumption.
- A JSON round-trip test for `LogicalOpNode` (each kind) and `ConditionalNode`.

## Frontend (`dbest-frontend`) — follow the mirror checklist in `plans/README.md`

The frontend hand-mirrors these node types; the port isn't done until it does too. New wire
tags: `"logicalOp"` (with `kind: "AND"|"OR"|"XOR"`) and `"conditional"` (with `condition`).

- `src/types/plan.ts` — add `"logicalOp"`, `"conditional"` to `NODE_TYPES`; add to the
  `PlanNode` union: `{ "@type":"logicalOp"; kind: LogicalKind }` and
  `{ "@type":"conditional"; condition: Condition }`. Add a `LOGICAL_KINDS = ["AND","OR","XOR"] as const`
  enum mirror next to `SET_KINDS`.
- `src/model/ports.ts` — add both tags to `BINARY` (both are two-input).
- `src/model/operators.ts` — add a new `OperatorCategory` (e.g. `"Conditional"`) to the type,
  `CATEGORY_ORDER`, and `PaletteOperator` chips: AND `&&`, OR `‖`, XOR `^` (each
  `template: () => ({ "@type":"logicalOp", kind })`), and IF `IF`
  (`template: () => ({ "@type":"conditional", condition: <placeholder cmp like filter's> })`).
- `src/model/nodes.ts` — `faceOf` cases (AND/OR/XOR pick the symbol from `kind`; IF → `"IF"`);
  add `"conditional"` to `EDITABLE` (it has a condition form). `logicalOp` is **not** editable
  (kind is fixed by the chip, like set-op variants).
- `src/model/summary.ts` — captions for both.
- `src/ui/form/` + `NodeForm.tsx` — IF reuses the existing condition builder from
  `FilterForm` (both carry a `Condition`); factor the shared bit if convenient.
- `src/i18n/strings.ts` — `opAnd/opOr/opXor/opIf` + `opDesc*` in en + pt-BR.
- `provider/parse.ts` — no change (node validation is driven by `NODE_TYPES`).

## Acceptance criteria
- **Backend:** `logicalAnd/Or/Xor` and `conditional` compile and execute against memory tables;
  `LogicalOpNode`/`ConditionalNode` round-trip through JSON; all `when`s updated; `./gradlew build` green.
- **Frontend:** chips appear in the palette, drop as unconfigured nodes, IF opens a condition
  form, the tree runs; `npm run build` + `npm run lint` green.
- PR notes the REFERENCE/CONDITION scoping decision.

## Out of scope
REFERENCE and standalone CONDITION unless the frontend requires them (see rationale above).
