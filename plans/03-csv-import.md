# Task 3 — CSV import — ✅ ALREADY DONE (optional polish only)

**Read `plans/README.md` first.** On inspecting `dbest-frontend` this task turned out to be
complete end to end. There is **no required work**. This doc records what exists and the only
optional polish, so nobody re-implements it.

## What already works (backend + frontend)

**Backend** — `src/1-http/FilePicker.kt` + `POST /pick-file` (`Routes.kt`):
- Opens a native **server-side** file dialog (browser never sees the real path; the engine reads
  the file by path — no bytes over HTTP). Header comment in `FilePicker.kt` explains the design.
- `.csv` → `{kind:"csv", path, name, separator, columns:[names], sample:[first row]}` with
  auto-detected separator.
- `.head` / `.dat` → `{kind, path, name}` for a BTree table (BTree reads its own schema).

**Frontend** — `src/ui/NewTableModal.tsx` (`ImportTableForm`) + `src/model/tables.ts`:
- Calls `api.pickFile()`, shows the path, lets the user set separator/delimiter/header line.
- **Infers column types** client-side via `guessColumnType(sample[i])`, pre-fills an editable
  `ColumnsEditor`, and commits `AddTable({@type:"csv", …})` via `POST /commands`.
- `.head`/`.dat` → commits `AddTable({@type:"btree", path})`.

So: pick → proposed schema → user edits → table on canvas. Done.

## Optional polish (only if inference quality is raised as a problem)

None of this is required; do it only on request:

1. **Multi-row sampling (backend).** `FilePicker.csvBody` reads a single sample line, so
   `guessColumnType` sees one value per column. Read up to ~50 data rows and return them (or a
   per-column inferred type) so mixed columns type correctly. This changes the `/pick-file` CSV
   response shape — mirror it in `types/tables.ts` (`PickedFile.csv`) and `parsePickedFile`.
2. **Richer `guessColumnType` (frontend, `model/tables.ts`).** Today it yields only
   INT/DOUBLE/BOOLEAN/STRING — it never proposes LONG or FLOAT, and doesn't infer `nullable`
   from blank cells. Widen it if the demo needs those types.

## Acceptance criteria
Nothing to build. If polish is requested, keep the `/pick-file` shape in lockstep across
`FilePicker.kt` ↔ `types/tables.ts` + `provider/parse.ts`, and keep `./gradlew build` +
`npm run build` green.
