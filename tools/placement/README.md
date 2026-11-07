# Placement data tools

Read the shared [data-format guide](../mongo-json-importer/mongodb-json-importer-data-format-guide.md)
and [importer README](../mongo-json-importer/README.md) before preparing an import.
The shared importer is read-only input to these tools; it is never modified.

## Canonical M1 (immutable)

```powershell
node tools/placement/build-mongo-seed.mjs --check
```

## DEV V3 package (local only)

Prerequisites: Node, JDK 21, Maven wrapper dependencies, native `mongosh` on PATH.
Run from `engolearn-backend`:

```powershell
node tools/placement/build-dev-package.mjs
node tools/placement/build-dev-package.mjs --check
```

Optional DEV origin (regenerate and check with the same argument):

```powershell
node tools/placement/build-dev-package.mjs --audio-base-url http://localhost:8892
node tools/placement/build-dev-package.mjs --check --audio-base-url http://localhost:8892
```

The default is `http://127.0.0.1:8892`, not a production URL. Changing the origin
does not start/reconfigure a media server. No files are copied into the JAR.

The wrapper checks M1 without writing, compiles existing Backend models/factory
with tests skipped, builds a temporary dependency classpath, and launches the Java
source tool. Temporary classpath files are removed. Build/cache files can change;
`--check` does not write the generated package or canonical datasets.

`BuildPlacementDevPackage.java` uses the real snapshot factory and an offline
`MappingMongoConverter` with `NoOpDbRefResolver`, not a Mongo client or Spring
application context. Output is deterministic UTF-8/LF with no wall-clock audit
timestamps. The canonical question documents keep their field/value order.

`validate-dev-package.mongosh.js` runs with `mongosh --nodb --norc`. It evaluates the
unmodified shared importer in memory, replaces only its `main()` invocation with
an export of its input-loader functions, and supplies a Mongo constructor that
always throws. It uses genuine mongosh EJSON, verifies the actual loader with
both `none` and default ObjectId patterns, checks Placement semantics independently,
and tests eight rejected input cases. It does not run importer DB preflight.
If the importer entry point changes, the harness fails and requires review.

Generated files are under `data/placement/dev-v3/`:

- **Import inputs only:** `placement-v3-questions.importer.json`,
  `placement-v3-test-sets.importer.json`.
- **Sidecars, never import:** `selection-manifest.json`, `audio-url-manifest.json`,
  `package-manifest.json`.

Canonical M1 has no audit timestamps, and wrapping it does not add/remove any.
The new snapshot similarly has no fabricated `created_at`/`updated_at`. Import
execution must retain a real operator/import log; the generic importer does not
invoke Spring auditing. Converter-generated `_class` on the snapshot is retained.

## Read-only proposed target inventory

```powershell
mongosh --nodb --norc --quiet --file tools/placement/inspect-dev-target.mongosh.js
```

This reads `127.0.0.1:27017` and checks whether the **proposed**, unapproved
`engolearn_placement_dev_v3` exists. It never creates a database, collection or
index. See [DEV preparation](../../data/placement/dev-v3/DEV-PREPARATION.md) for
execution prerequisites. Do not run the shared `import.ps1` yet: even `-DryRun`
unconditionally starts the Compose MongoDB service before its read-only preflight.
