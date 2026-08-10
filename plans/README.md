# Port plans — finishing DBest → model-adapter2 (+ dbest-frontend)

One self-contained plan per remaining port task, written to be executed in a **fresh session**
(the user runs `/clear` between tasks), so every plan repeats the context it needs. Read this
index first, then the task file.

**Two repos.** The port spans both:
- **`/home/cleanh/model-adapter2`** — backend: engine adapter + session model + JSON codec +
  HTTP API. Legacy behaviour reference: `$DBEST_HOME` (`/home/cleanh/Desktop/adaptadorDbest/DBest`).
- **`/home/cleanh/dbest-frontend`** — the React canvas (React 19 + TypeScript + Vite +
  `@xyflow/react`) that replaces the Swing editor. It **hand-mirrors** the backend vocabulary.

Most tasks touch **both repos**: a new operator/source/format only "exists" once the frontend
mirror and the backend agree. Each plan has a Backend and a Frontend section.

> Wire contract: **`model-adapter2/VIEW-MODEL-PROTOCOL.md`** (both repos' `README` and the
> frontend `vite.config.ts` §2 point at it). It's transcribed from the server codecs and is the
> source of truth both `src/json/*.kt` and `dbest-frontend/src/types/*` mirror. When a task
> changes the wire, update that doc in the same change.

## Tasks & status

| # | Plan | Status | Repos |
|---|------|--------|-------|
| 3 | [03-csv-import.md](03-csv-import.md) | ✅ **already done** end-to-end — optional polish only | both (done) |
| 1 | [01-conditional-operators.md](01-conditional-operators.md) | to do — `IF/AND/OR/XOR` (skip `REFERENCE/CONDITION`) | backend + frontend |
| 2 | [02-jdbc-xml-sources.md](02-jdbc-xml-sources.md) | to do — XML first, then JDBC | backend + frontend |
| 4 | [04-export.md](04-export.md) | to do — new backend module + a download affordance | backend + frontend |

DSL is intentionally skipped. Recommended order: **1 → 2 → 4** (Task 3 needs nothing).

---

## Backend architecture (`model-adapter2`)

Four layers, one package each, dependencies point down only:

```
src/1-http     dbest.http    http4k routes, Canvas facade, FilePicker, error filter
src/2-canvas   dbest.model   session graph ADT (Node/TableSpec/Command), pure apply/invert,
                             Query.kt (node→adapter Plan), Runtime.kt (spec→TableHandle)
src/json       dbest.json    wire codecs: @type-tagged JSON, round-trippable
src/3-engine   dbest.adapter the ONLY package allowed to import ibd.*  (the engine)
src/misc       dbest.misc    functional helpers used project-wide instead of stdlib
```

**Hard rules:**
1. **`ibd.*` only inside `dbest.adapter`.** Query `Operation`s built only under
   `adapter/compile/`; tables only in `adapter/Tables.kt`. Layers above never import `ibd.*`.
2. **ADTs are `sealed interface` + `data class`, matched with exhaustive `when` (no `else`).**
   Adding a variant deliberately breaks compilation at every match site — that's the feature.
3. **`data class`es validate in `init { require(cond){ "<msg pt-BR>" } }`.** Messages Portuguese,
   identifiers English.
4. **Every type has a lowercase factory** next to it (the `f(x)` convention in `src/dictionary`).
5. **Use `dbest.misc` helpers**, not stdlib collection ops. Explicit types on `val`s/params.
6. **Codecs round-trip.** Each `json(x)` has a matching `xOf(element)`; optionals omitted at
   default via `valueUnless`/`transformOr`; unknown tags → `wireError`.
7. **Errors** via `EngineException` + the `gate { }` wrapper (`adapter/Errors.kt`). Raw `ibd.*`
   calls live inside `gate { }`.
8. **Tests** mirror the source package under `test/`; `test/adapter/SmokeTest.kt` is the model.

### Backend blast radius (exhaustive `when` sites)
- **New operator (Node):** `adapter/Plan.kt` (+factory) · `adapter/compile/Compiler.kt` (+maybe
  `compile/Unary.kt`/`Binary.kt`) · `model/Nodes.kt` (`operatorKind`) · `model/Query.kt` (`plan`) ·
  `json/Nodes.kt` (`json` + `nodeOf`).
- **New source (TableSpec):** `adapter/Tables.kt` (`xxxTable(): TableHandle`) · `model/TableSpecs.kt` ·
  `model/Runtime.kt` (`openTable`) · `json/TableSpecs.kt` (`json` + `tableSpecOf`).
- **New command:** `model/Commands.kt` (`apply`+`invert`) · `json/Commands.kt`.

---

## Frontend architecture (`dbest-frontend`)

Layered like the backend, dependencies inward: `ui → demo → model → types`, with `provider`
beside them. Only `src/ui/` (and `i18n/index.tsx`) import React; `types/`, `provider/`,
`model/`, `i18n/core.ts` are framework-free. **The server owns the canvas** — every gesture is a
`Command` POSTed to `/commands`; the client applies nothing locally and refetches `GET /session`
after each mutation (loopback, so the read is free). Rows/schema are fetched fresh, never mirrored.

Key idea: adding a variant is driven by **small const-array levers**, and the parser (`provider/
parse.ts`) validates only the *envelope + `@type`/enum tags* then trusts leaf values — so no
per-field parser is needed for a new node/spec.

### Frontend mirror checklist — adding an operator
- `src/types/plan.ts` — add the tag to `NODE_TYPES` **and** a variant to the `PlanNode` union.
- `src/model/operators.ts` — add a `PaletteOperator` chip (symbol, `nameKey`, `descKey`,
  category, `template()` returning the unconfigured placeholder). Add a new `OperatorCategory`
  + `CATEGORY_ORDER` entry if it doesn't fit the existing seven.
- `src/model/ports.ts` — if binary, add the tag to `BINARY`.
- `src/model/nodes.ts` — add a `faceOf` case (symbol + localized name); add to `EDITABLE` if it
  has a parameter form.
- `src/model/summary.ts` — add its one-line caption.
- `src/ui/form/` + `src/ui/NodeForm.tsx` — add a parameter form if editable (reuse pieces:
  `FilterForm` already builds a `Condition`).
- `src/i18n/strings.ts` — add the `op*`/`opDesc*` keys in both languages.
- `provider/parse.ts` needs **no change** for a new node (validation is driven by `NODE_TYPES`).

### Backend↔frontend endpoint surface (already wired)
`GET /session` · `POST /commands` (any `Command`) · `POST /undo|/redo` · `GET /roots` ·
`GET /problems` · `GET /nodes/{id}/rows|schema|exists` · `POST /pick-file`. The `api` object in
`src/provider/client.ts` wraps each. New endpoints (e.g. export) get a new `api.*` method there.

---

## Before starting any task
Read the real code in both repos — it's the source of truth; these plans only point the way.
Backend: `Plan.kt`, `compile/*.kt`, `Conditions.kt`, `Tables.kt`, `Nodes.kt`, `Query.kt`,
`Runtime.kt`, `TableSpecs.kt`, the `json/` counterparts, `misc/free-utils.kt`.
Frontend: `types/plan.ts`, `types/tables.ts`, `model/operators.ts`, `model/nodes.ts`,
`model/ports.ts`, `provider/parse.ts`, `ui/NewTableModal.tsx`, `ui/form/`.
