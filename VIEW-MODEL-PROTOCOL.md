# VIEW-MODEL-PROTOCOL

The wire contract between **model-adapter2** (the server that owns the canvas) and any client
(today: **dbest-frontend**). This document is the single source of truth both sides mirror:

- Server codecs (authoritative): `src/json/*.kt` — `Nodes.kt`, `TableSpecs.kt`, `Commands.kt`,
  `Session.kt`, `History.kt`, `AdapterCodec.kt`, `JsonTree.kt`; envelopes in `src/1-http/`.
- Client mirror: `dbest-frontend/src/types/*.ts` (vocabulary) + `src/provider/parse.ts` (boundary).

It is transcribed from those codecs; if code and doc disagree, the code wins — fix the doc.
Every payload is JSON (`application/json; charset=utf-8`).

---

## 1. Principles

- **The server owns the one true canvas.** The client holds a read-only mirror. It never mutates
  locally: every gesture becomes a `Command` (§7) POSTed to `/commands`; after each mutation the
  client refetches `GET /session` and replaces its mirror. Revisions are monotonic; the current
  revision rides on the `ETag` header of every mutation/session response.
- **Rows and schema are never mirrored** — they are fetched fresh from the engine per node (§3).
- **Discriminated unions are tagged by `@type`** (nodes, table specs, conditions, commands); the
  pick-file result is tagged by `kind`.
- **Defaults are omitted.** The encoder drops a field whose value equals its default; the decoder
  restores that default when the field is absent. Defaults are listed per type below. A client
  must tolerate omission and should likewise omit at default when sending.
- **Parse at the boundary, envelope + tags.** The client validates the envelope shape and every
  `@type`/enum tag, then trusts leaf values (`provider/parse.ts`). New tags are admitted by
  extending the enum arrays (`NODE_TYPES`, `TABLE_SPEC_KINDS`, `PICKED_KINDS`).

## 2. Transport & endpoints

Same-origin only. In dev, the Vite server proxies these paths to the backend; the backend has no
CORS filter, so the client must hit its **own origin**, never `:8000` directly.

| Method | Path | Body | 200 response | Notes |
|--------|------|------|--------------|-------|
| GET  | `/session` | — | `SessionView` (§3.1) | sets `ETag: <revision>` |
| POST | `/commands` | `Command` (§7) | `Ack` (§3.2) | sets `ETag`; validates + applies |
| POST | `/undo` | — | `Ack` | |
| POST | `/redo` | — | `Ack` | |
| POST | `/pick-file` | — | `PickedFile` (§3.6) | opens a native dialog **on the server host** |
| GET  | `/roots` | — | `NodeId[]` (ints) | nodes nothing consumes (query roots) |
| GET  | `/problems` | — | `Problem[]` (§3.4) | validation issues for the whole session |
| GET  | `/nodes/{id}/rows` | — | `RowsPage` (§3.3) | query `?offset=&limit=` (both or neither) |
| GET  | `/nodes/{id}/schema` | — | `SchemaColumn[]` (§5.7) | output columns of the subtree at `{id}` |
| GET  | `/nodes/{id}/exists` | — | `{ "exists": boolean }` | true iff the subtree yields ≥1 row |
| GET  | `/nodes/{id}/export` | — | **file** (not JSON) | `?format=csv\|sql` (default `csv`), `?table=` (default `export`); `Content-Disposition: attachment` |

`{id}` is a node id (integer). Unknown id → 404.

`/nodes/{id}/export` is the one endpoint whose body is **not** a mirrored JSON envelope: it streams
the node's result as a downloadable `text/csv` or `application/sql` file (RFC 4180 CSV; ANSI SQL
`CREATE TABLE` + `INSERT`s). Column headers/identifiers follow the legacy `exportToCSV` rule —
bare `name` when all are unique, else every column falls back to qualified `source.name` (all-or-
nothing, so join outputs like `u.id`/`o.id` stay distinct). Unknown `format` → 400. The client must
**not** route it through the JSON `request()` helper — use a plain link / `window.location` so the
browser saves the file.

### Errors
Any non-2xx carries `{ "error": "<message>" }`. Status mapping (`src/1-http/Errors.kt`):

| Status | Meaning |
|--------|---------|
| 400 Bad Request | malformed body / unknown `@type` / failed `require` (invalid node or command args) |
| 404 Not Found | route id absent from the session |
| 422 Unprocessable Entity | plan is semantically invalid (engine `PlanError`) |
| 502 Bad Gateway | engine storage failure |
| 500 Internal Server Error | any other engine failure |

## 3. Envelopes

### 3.1 SessionView — `GET /session`
```json
{ "revision": 7, "depth": 3, "session": { …Session (§4.1) }, "canUndo": true, "canRedo": false }
```
`depth` = size of the undo stack. `session` omits empty sub-maps (see §4.1).

### 3.2 Ack — `/commands`, `/undo`, `/redo`
```json
{ "revision": 8, "depth": 4, "canUndo": true, "canRedo": false, "applied": { …Command } }
```
`applied` is the `Command` that produced this revision (echoed), or `null`.

### 3.3 RowsPage — `/nodes/{id}/rows`
```json
{ "rows": [ { "u.name": {"str":"Ana"}, "u.age": {"int":22} } ], "elapsedMs": 1.84 }
```
Each row is an object keyed `"<alias>.<column>"` → `Literal` (§5.2). `elapsedMs` times the engine
work only (compile + run + drain), excluding JSON/network. Absent `rows` ⇒ empty.

### 3.4 Problem — `/problems`
```json
{ "node": 5, "message": "O node #5 esta sem sua entrada LEFT" }
```

### 3.5 SchemaColumn[] — `/nodes/{id}/schema`  → see §5.7.

### 3.6 PickedFile — `/pick-file`
Tagged by `kind` (`PICKED_KINDS = ["cancelled","csv","head","dat"]`). The server returns the
absolute **path** of a file chosen in a native dialog on the server host — never the bytes.
```json
{ "kind": "cancelled" }
{ "kind": "csv",  "path": "/abs/orders.csv", "name": "orders",
  "separator": ",", "columns": ["id","total"], "sample": ["10","250"] }
{ "kind": "head", "path": "/abs/t.head", "name": "t" }
{ "kind": "dat",  "path": "/abs/t.dat",  "name": "t" }
```
`csv` carries the header names and one sample row (client infers column types). `head`/`dat` are
BTree tables whose schema the engine reads from the file itself. Unsupported extension → 400.

## 4. Session model

### 4.1 Session
```json
{ "tables": { "1": {…TableSpec} }, "nodes": { "3": {…Node} },
  "edges": [ {…Edge} ], "layout": { "3": {"x":120,"y":40} } }
```
`tables`, `nodes`, `layout` are **objects keyed by the stringified integer id**. `edges` is an
array. Every one of the four is omitted when empty.

### 4.2 Ids, Position, Edge, Port
- `NodeId`, `TableId` — bare integers on the wire.
- `Position` — `{ "x": number, "y": number }` (doubles).
- `Port` — one of `"ONLY" | "LEFT" | "RIGHT"`.
- `Edge` — `{ "from": <nodeId>, "to": <nodeId>, "port": Port }`. `from`'s output feeds `to`'s
  named input port. Source nodes have no input port; binary nodes expose `LEFT`+`RIGHT`; every
  other node exposes `ONLY` (§5.1).

## 5. Nodes & shared vocabulary

### 5.1 The node ADT (`@type`, 20 kinds)
Arity ⇒ input ports: **source** = none, **binary** = `LEFT`+`RIGHT`, **unary** = `ONLY`.
`?` marks a field the encoder omits at the listed default.

| `@type` | arity | fields (default) |
|---------|-------|------------------|
| `scan` | source | `table` (TableId int), `alias` |
| `filter` | unary | `condition` (§5.3) |
| `project` | unary | `columns: string[]` (≥1) |
| `removeColumns` | unary | `columns: string[]` (≥1), `alias?` = `"Projection"` |
| `sort` | unary | `keys: SortKey[]` (≥1) (§5.4) |
| `distinct` | unary | `hashed?` = `true` |
| `limit` | unary | `count` (>0), `offset?` = `0` |
| `alias` | unary | `from`, `to` |
| `collapse` | unary | `alias` |
| `explode` | unary | `column`, `delimiter?` = `","` |
| `rowNumber` | unary | `alias`, `column`, `start?` = `1` |
| `agg` | unary | `alias`, `by`: `QualifiedCol \| null`, `aggregates: Agg[]` (≥1), `hashed?` = `true` |
| `materialize` | unary | — |
| `memoize` | unary | — |
| `hashIndex` | unary | — |
| `join` | binary | `on: JoinTerm[]` (≥1), `type?` = `INNER`, `algorithm?` = `NESTED_LOOP` |
| `cross` | binary | — |
| `setOp` | binary | `kind: SetKind`, `hashed?` = `true` |
| `logicalOp` | binary | `kind: LogicalKind` |
| `exists` | binary | `bilateral?` = `false` |

Notes: `agg.by` is **always present**, either a `QualifiedCol` (§5.5) or `null`. `distinct.hashed`,
`setOp.hashed` and `exists.bilateral` only ever appear at their **non-default** value (the default
is implied by absence). Not every `join` `type`×`algorithm` is executable — the engine rejects
`FULL`, `RIGHT_SEMI`, `RIGHT_ANTI` under `NESTED_LOOP` at run time (422). `logicalOp` does **not**
emit data rows: it yields a single boolean row `{ "condition.EVAL": bool }` combining the two
sides — `AND`/`OR` test whether both/either side has rows, `XOR` only discriminates when a side is
itself a `logicalOp` (a bare data source counts as satisfied). (The legacy `IF` operator is not
exposed: it needs a correlated evaluation context the canvas's root execution never provides.)

### 5.2 Literal (tagged, single key)
`null`, or exactly one of: `{"int":n}`, `{"long":n}`, `{"float":n}`, `{"double":n}`,
`{"bool":b}`, `{"str":s}`, `{"ref":"src.col"}` (or `{"ref":"col"}` unqualified). `ref` denotes a
column reference used as a value (e.g. column-vs-column comparison). A `Row` value and a memory
table cell are `Literal`s.

### 5.3 Condition (tagged by `@type`)
```json
{ "@type": "cmp", "left": {ColumnRef}, "op": CompareOp, "right": {Literal} }
{ "@type": "isNull",    "column": {ColumnRef} }
{ "@type": "isNotNull", "column": {ColumnRef} }
{ "@type": "and", "conditions": [ …Condition ] }   // ≥2
{ "@type": "or",  "conditions": [ …Condition ] }   // ≥2
```
`cmp.right` is a `Literal` and must not be `null` (use `isNull`/`isNotNull`); it may be `{"ref":…}`
for column-vs-column. `CompareOp` ∈ `EQ, NEQ, GT, GTE, LT, LTE`.

### 5.4 `SortKey` — `{ "column": string, "ascending": boolean }`
### 5.5 `ColumnRef` — `{ "source": string|null, "name": string }` · `QualifiedCol` — `{ "source": string, "column": string }`
`ColumnRef.source` may be `null` (unqualified). `QualifiedCol` requires both (join terms /
group-by are always qualified). *(Note the field-name asymmetry: `ColumnRef` uses `name`,
`QualifiedCol` uses `column`.)*
### 5.6 `JoinTerm` — `{ "left": QualifiedCol, "right": QualifiedCol }` · `Agg` — `{ "column": string, "function": AggFunction }`
### 5.7 `Column` / `SchemaColumn`
```json
Column       { "name": string, "type": ColumnType, "primaryKey": boolean, "nullable": boolean }
SchemaColumn { "source": string, "name": string, "type": string, "primaryKey": boolean }
```
`Column.type` is a `ColumnType` enum (§8); `SchemaColumn.type` is a **raw engine string**, not a
`ColumnType`.

## 6. Table specs (tagged by `@type`, `TABLE_SPEC_KINDS = ["memory","csv","btree","xml"]`)
```json
{ "@type": "memory", "name": string, "columns": [Column], "rows?": [Row] }
{ "@type": "csv",    "name": string, "path": string, "columns": [Column],
  "separator?": ",", "delimiter?": "\"", "headerLine?": 1 }
{ "@type": "btree",  "name": string, "path": string, "cacheSize?": 100000 }
{ "@type": "xml",    "name": string, "path": string, "columns": [Column],
  "rootElement": string|null, "recordElement": string|null }
```
`memory.rows` omitted when empty. `separator`/`delimiter` are single characters. Table `name` is
how the engine indexes the relation, so it must be unique within a session. `xml` is read-only;
`rootElement`/`recordElement` are always present, `null` meaning the engine auto-detects them.
Declared `columns` are matched by name against the flattened XML (child elements by tag name,
attributes as `@attr`); values are coerced to the declared `ColumnType`. Note: the engine's
XMLTable ignores delegated filters, so a `filter` over an XML scan is not applied at the source.

## 7. Commands (tagged by `@type`)
```json
{ "@type": "addTable",    "id": <tableId>, "spec": {TableSpec} }
{ "@type": "removeTable", "id": <tableId> }
{ "@type": "addNode",     "id": <nodeId>, "node": {Node}, "at": {Position} }
{ "@type": "setNode",     "id": <nodeId>, "node": {Node} }     // reconfigure; same @type only
{ "@type": "removeNode",  "id": <nodeId> }
{ "@type": "connect",     "edge": {Edge} }
{ "@type": "disconnect",  "edge": {Edge} }
{ "@type": "move",        "id": <nodeId>, "to": {Position} }
{ "@type": "batch",       "commands": [ …Command ] }           // applied atomically, in order
```
Server-enforced invariants (violations → 400): ids unique on add / present on mutate; `setNode`
cannot change a node's `@type`; an input port accepts one edge; no self-feed; **no cycles**; a
`scan` may only reference an existing table; a table may not be removed while a `scan` references
it. `batch` is one undo unit.

## 8. Enums

| Enum | Values |
|------|--------|
| `Port` | `ONLY, LEFT, RIGHT` |
| `CompareOp` | `EQ, NEQ, GT, GTE, LT, LTE` |
| `AggFunction` | `MAX, MIN, COUNT, AVG, SUM, FIRST, LAST, COUNT_ALL, COUNT_NULL` |
| `JoinType` | `INNER, LEFT, RIGHT, FULL, LEFT_SEMI, RIGHT_SEMI, LEFT_ANTI, RIGHT_ANTI` |
| `JoinAlgorithm` | `NESTED_LOOP, HASH, MERGE` |
| `SetKind` | `UNION, INTERSECT, EXCEPT, APPEND` |
| `LogicalKind` | `AND, OR, XOR` |
| `ColumnType` | `INT, LONG, FLOAT, DOUBLE, STRING, BOOLEAN` |
| `PickedFile.kind` | `cancelled, csv, head, dat` |

## 9. Persistence (out of band)

Saved project files (`.dbest`) are **not** part of the HTTP protocol; they are a `History`
document (`src/json/History.kt`) written/read server-side:
```json
{ "version": <int>, "history": { "session": {Session}, "undoStack": [Step], "redoStack": [Step],
                                 "limit?": 200 } }
```
where `Step = { "redo": {Command}, "undo": {Command} }`. Empty stacks / default session omitted.
Documented here only so the vocabulary is complete; clients use `/session`, not this file.

---

### Extending the protocol
Add a variant on **both** sides in lockstep: a server codec branch (`src/json/*`) + its ADT, and
the client mirror (`types/*.ts` union + the relevant `*_KINDS`/`NODE_TYPES` array). Because the
client parser is envelope-and-tags, a new node/spec needs only its tag registered plus the union
case — no per-field parser. Keep this doc in step with the codecs in the same change.
