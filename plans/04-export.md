# Task 4 — Export query results (CSV / SQL, extensible)

**Read `plans/README.md` first.** This is the one task that needs a **new module**: nothing in
the project writes output today. Legacy exports a query's result to CSV / Excel / SQL / DAT /
XML (`enums/FileType.java`, `files/ExportFile.java`). Do the two pure, dependency-free formats
first (**CSV and SQL**) and leave the rest as documented extension points.

## What already exists to build on

A node's result and shape are one call each away, through the `Canvas` facade (`src/1-http/Canvas.kt`):
- `canvas.rows(id): List<Map<String, Any?>>` — rows keyed `"alias.column"`, already in schema order.
- `canvas.schema(id): List<SchemaColumn>` — `SchemaColumn(source, name, type, primaryKey)`, ordered.

So export is a **pure transform** `(schema, rows) → text`, plus an HTTP delivery route. No engine
access needed — it consumes adapter vocabulary only.

## New module: `src/export` (package `dbest.export`)

Follow the unnumbered-utility-root convention (`src/json`, `src/misc`). **Register it in
`build.gradle.kts`** — source roots are explicit:
```kotlin
main { kotlin.setSrcDirs(listOf("src/1-http", "src/2-canvas", "src/3-engine", "src/json", "src/misc", "src/export")) }
```
`dbest.export` may import `dbest.adapter` (`SchemaColumn`) and `dbest.misc`; **never `ibd.*`**.

### Formatters (pure)
```kotlin
// dbest.export
enum class ExportFormat { CSV, SQL }

fun exportRows(format: ExportFormat, table: String, schema: List<SchemaColumn>,
               rows: List<Map<String, Any?>>): String = when (format) {
    ExportFormat.CSV -> csvExport(schema, rows)
    ExportFormat.SQL -> sqlExport(table, schema, rows)
}
```
- Derive each column's row-map key as `"${col.source}.${col.name}"`; use `schema` for **order
  and headers** (don't iterate map keys).
- **CSV** (RFC 4180): header row of column keys (or bare `name` — decide with the frontend),
  then values; quote fields containing `, " \n`, double interior quotes; `null` → empty field.
- **SQL**: a `CREATE TABLE "<table>" (...)` from schema types + PK, then one `INSERT INTO`
  per row. String literals single-quoted and escaped, numbers raw, `null` → `NULL`, booleans
  per dialect. Keep dialect assumptions minimal / documented.
- Keep both exhaustive over `ExportFormat` (no `else`) so a new format breaks compilation here.

### Delivery — `src/1-http/Routes.kt`
Add a route (no new command; export is a read):
```kotlin
"/nodes/{id}/export" bind GET to { request -> exportResponse(canvas, request) }
```
`exportResponse` reads `?format=` (default `csv`) and `?table=` (default the node's exposed
alias or `"export"`), calls `canvas.schema(id)` + `canvas.rows(id)`, then `exportRows(...)`,
and returns `Response(OK)` with `Content-Type` (`text/csv` / `application/sql`) and
`Content-Disposition: attachment; filename="<table>.<ext>"`. Route errors through the existing
`errorFilter` (unknown format → `IllegalArgumentException`, mirror `intParam`'s style).

Reuse `nodeId(canvas, request)` for id validation. Consider honouring `?offset=&limit=` like
`rowsResponse` so large exports can be paged (optional).

## Tests (`test/export/`, mirroring the package)
- `csvExport`: quoting (comma / quote / newline), null → empty, column order follows schema.
- `sqlExport`: string escaping, null → NULL, numeric vs string literals, CREATE + INSERT shape.
- An http-level test through `router(...)` asserting `Content-Disposition` and body for a small
  in-memory table (see `RoutesTest.kt` for the pattern).

## Deferred (extension points — document, don't build yet)
- **Excel `.xlsx`** — needs a new dependency (`org.apache.poi:poi-ooxml`). Add an
  `ExportFormat.EXCEL` branch returning bytes (change the return type to `ByteArray` behind a
  small wrapper, or add a parallel `exportBytes`) when prioritised.
- **XML / DAT** — legacy/engine-specific; add as `ExportFormat` variants when a concrete need
  appears.
- **Server-side save dialog** — for symmetry with import (`FilePicker` opens a native *load*
  dialog and writes by path), an alternative delivery is a native *save* dialog that writes the
  file server-side. Recommended default is the HTTP download above (works even if server and
  browser aren't co-located); offer the save-dialog only if the frontend wants parity.

## Frontend (`dbest-frontend`) — a download affordance

The export response is a **file, not JSON**, so it must not go through `provider/client.ts`'s
`request()` (which JSON-parses every body). Two clean options:

- **Simplest:** a plain link/button that navigates to the URL and lets the browser download via
  the `Content-Disposition` header — e.g. in `src/ui/Results.tsx` (the tuples popup, which
  already has the node in context) add "Export CSV" / "Export SQL" controls that do
  `window.location.assign(\`/nodes/${node}/export?format=csv\`)` (dev server proxies it).
- **Or:** an `api.exportUrl(node, format)` helper returning the path, used by an `<a download>`.
  Avoid adding it as a parsed `request` method.

Also add the node-toolbar entry in `src/ui/Canvas.tsx` if you want export next to **Run**
(gate it the same way — only for runnable/legal nodes). Add `exportCsv`/`exportSql` (+ any
"Export" label) to `src/i18n/strings.ts` in en + pt-BR. No `types/`/`parse.ts` change (it's not
a `Command` and not a mirrored response).

## Acceptance criteria
- **Backend:** `src/export` registered and building, `dbest.export` free of `ibd.*`;
  `GET /nodes/{id}/export?format=csv|sql` downloads a correct file for any runnable node;
  formatters pure and unit-tested (quoting/escaping/null/order); `./gradlew build` green.
- **Frontend:** a runnable node can be exported to CSV/SQL from the UI and the browser saves the
  file; `npm run build` + lint green.
