# Task 2 — New data sources: XML and JDBC

**Read `plans/README.md` first.** This adds two `TableSpec` variants. Follow the *source*
blast radius: `Tables.kt` (adapter) → `TableSpecs.kt` + `Runtime.kt` (model) → `TableSpecs.kt`
(json). Do **XML first** (file-based, no deps, no live resource), then JDBC.

Both engine table types already exist and slot in exactly like `csvTable` / `openBTreeTable`:

| Source | Engine class | Constructor |
|--------|--------------|-------------|
| XML | `ibd.table.xml.XMLTable` | `(Header header, String rootElement, String recordElement, XMLRecognizer.FlatteningStrategy strategy)` |
| JDBC | `ibd.table.jdbc.JDBCTable` | `(Header header, String connectionUrl, String connectionUser, String connectionPassword)` |

Study `csvTable(...)` in `adapter/Tables.kt` — it is the template: build a `Prototype` from
`Column`s, wrap in a `Header`, set the path, construct the engine table, `open()`, wrap in
`TableHandle`. Everything runs inside `gate { }`.

---

## Part A — XML (do this first)

`XMLTable` reads a header whose path is set via `header.set(Header.FILE_PATH, path)` (same as
CSV; the ctor reads `header.getTablePath()` and derives the name from the filename). It is
read-only. `rootElement`/`recordElement` may be null → engine auto-detects; strategy defaults
to `XMLRecognizer.FlatteningStrategy.NESTED_COLUMNS`.

### Adapter — `src/3-engine/adapter/Tables.kt`
```kotlin
import ibd.table.xml.XMLTable
import ibd.table.xml.XMLRecognizer

fun xmlTable(
    path: String, name: String, vararg columns: Column,
    rootElement: String? = null, recordElement: String? = null,
): TableHandle = gate {
    val header = Header(prototype(*columns), name)
    header.set(Header.FILE_PATH, path)
    val table = XMLTable(header, rootElement, recordElement, XMLRecognizer.FlatteningStrategy.NESTED_COLUMNS)
    table.open()
    TableHandle(table)
}
```
Verify the exact `Header` API used by `csvTable` (it calls `header.set(Header.FILE_PATH, path)`)
and reuse identically. Confirm `XMLTable.open()` exists and that `header.getTablePath()`
resolves what you set.

### Model — `src/2-canvas/model/TableSpecs.kt`
```kotlin
data class XmlSpec(
    override val name: String, val path: String, val columns: List<Column>,
    val rootElement: String? = null, val recordElement: String? = null,
) : TableSpec
```

### Model — `src/2-canvas/model/Runtime.kt` (`openTable` branch)
```kotlin
is XmlSpec -> xmlTable(spec.path, spec.name, *spec.columns.toTypedArray(),
                       rootElement = spec.rootElement, recordElement = spec.recordElement)
```

### JSON — `src/json/TableSpecs.kt`
Encoder branch (omit optionals at their default with `valueUnless`/`transformOr`):
```kotlin
is XmlSpec -> obj(
    "@type" to json("xml"), "name" to json(spec.name), "path" to json(spec.path),
    "columns" to JsonArray(mapCollection(spec.columns, ::json)),
    "rootElement" to transformOr(spec.rootElement, ::json, JsonNull),
    "recordElement" to transformOr(spec.recordElement, ::json, JsonNull),
)
```
Decoder branch `"xml" -> XmlSpec(...)` using `fields.stringOrNull("rootElement")` etc.

### Test
Add a small `.xml` fixture under `test/model/` and a round-trip + `execute` test (mirror how
CSV/BTree are tested). Assert rows come back with the declared columns.

---

## Part B — JDBC

`JDBCTable` needs a live `java.sql.Connection` (url/user/password) and `header.set("tablename", …)`.
Unlike file sources this has **three cross-cutting concerns** — address each explicitly:

1. **Drivers are not on the classpath.** `build.gradle.kts` currently has no JDBC drivers. Add
   `runtimeOnly(...)` for the dialects the course needs (legacy supported MySQL, PostgreSQL,
   Oracle, SQLite, MariaDB). **Recommend SQLite first** (`org.xerial:sqlite-jdbc`) — single
   file, no server, trivial to fixture in tests — then add others as needed. Keep them
   `runtimeOnly` so the adapter never imports a driver directly.
2. **Live resource lifecycle.** File tables are cheap to reopen; a JDBC connection is not, and
   `OpenTables` caches one `TableHandle` per spec and closes them in `closeTables`. Confirm
   `JDBCTable.close()` actually closes the `Connection`; if not, the cache leaks connections.
   Do **not** open a connection at spec-construction time — only inside `openTable`/`gate`.
3. **Secrets.** A `JdbcSpec` with a password becomes part of `Session`, is serialized into the
   `.dbest` file by `Persistence.save`, and is a `ConcurrentHashMap` key in `OpenTables`. Decide
   the policy and write it down: simplest correct option is **do not persist the password** —
   keep url/user/table in the spec, resolve the password at open time from an env var or a
   per-session prompt the frontend supplies. Flag this to the user before implementing; it
   changes the spec shape.

### Adapter — `src/3-engine/adapter/Tables.kt`
```kotlin
import ibd.table.jdbc.JDBCTable

fun jdbcTable(
    url: String, user: String, password: String,
    tableName: String, vararg columns: Column,
): TableHandle = gate {
    val header = Header(prototype(*columns), tableName)
    header.set("tablename", tableName)
    val table = JDBCTable(header, url, user, password)
    table.open()
    TableHandle(table)
}
```
Confirm against the engine whether the prototype/columns are required or whether `JDBCTable`
can introspect the DB schema itself (check `JDBCTable.open()` / how it builds its prototype).
If it introspects, the spec may not need a `columns` list at all.

### Model / JSON
`JdbcSpec(name, url, user, table, columns?)` in `TableSpecs.kt`; `openTable` branch calling
`jdbcTable(...)`; codec branch `"jdbc" -> JdbcSpec(...)`. Apply the secrets decision from #3 to
which fields exist and which are serialized.

### Test
SQLite fixture: create a temp `.db` in the test, point a `JdbcSpec` at
`jdbc:sqlite:/tmp/....db`, `execute` and assert rows. Keep it hermetic (temp file, cleaned up).

## Frontend (`dbest-frontend`) — table authoring lives in `ui/NewTableModal.tsx`

Sources are added through the New-Table modal, not the operator palette. Today
`TableSpec` (both `src/types/tables.ts` and the `TABLE_SPEC_KINDS` array in `provider/parse.ts`)
knows `memory | csv | btree`, and `NewTableModal` has a `CreateTableForm` (memory) and
`ImportTableForm` (csv/head/dat via `api.pickFile`). Add XML and JDBC alongside.

Shared:
- `src/types/tables.ts` — extend the `TableSpec` union with the `xml` and `jdbc` variants,
  matching the backend spec fields exactly (apply the secrets decision to which JDBC fields exist).
- `src/provider/parse.ts` — add `"xml"`, `"jdbc"` to `TABLE_SPEC_KINDS` (that's all the parser
  needs; it trusts leaf values after the tag check).
- `src/i18n/strings.ts` — labels for the new modes/fields (en + pt-BR).
- `src/ui/useCanvas.ts` — `NewTableMode` currently is `"create" | "import"`; add modes (e.g.
  `"xml"`, `"jdbc"`) and a header/menu entry to open each (see how `import` is triggered).

XML (**file-based — reuse the picker**):
- Backend: extend `FilePicker` to accept `.xml` and return `{kind:"xml", path, name}`; add
  `"xml"` to `PICKED_KINDS` (`types/tables.ts`) and a branch in `parsePickedFile`.
- Frontend form: like `ImportTableForm` but committing an `xml` spec; columns can be
  user-declared (reuse `ColumnsEditor`) or, if the engine introspects, skipped. Optional
  root-element / record-element inputs (leave blank → engine auto-detect).

JDBC (**no picker — a connection form**):
- New form in `NewTableModal`: inputs for url, user, password, table name, and columns (or a
  "fetch schema" affordance if the backend introspects). Commit a `jdbc` spec via `POST /commands`.
- Respect the password policy from Part B.3 — if the password isn't persisted, the form collects
  it per session and sends it on the command (and the spec on the wire omits it).

## Acceptance criteria
- **Backend:** XML `.xml` fixture and JDBC SQLite fixture both load and query; `XmlSpec`/`JdbcSpec`
  round-trip through JSON; JDBC drivers `runtimeOnly`, connection closed on `closeTables`,
  secrets policy implemented; `ibd.*` still only under `dbest.adapter`; `./gradlew build` green.
- **Frontend:** New-Table modal can create an XML source (via picker) and a JDBC source (via
  connection form), both landing a scannable table on the canvas; `npm run build` + lint green.

## Open questions to resolve with the user before JDBC
- Which SQL dialects to bundle now vs later.
- Password persistence policy (see B.3).
- Whether columns are user-declared or DB-introspected.
