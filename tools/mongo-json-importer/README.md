# Generic MongoDB JSON Importer

A domain-agnostic development tool for importing JSON datasets into MongoDB. It uses a tool-local .env for importer options and reads only shared MongoDB credentials/database defaults from the repository root .env. No application-specific schema or collection names are hard-coded.

## Quick start

Run commands from the repository root:

    Copy-Item .env.example .env
    Copy-Item .\tools\mongo-json-importer\.env.example .\tools\mongo-json-importer\.env

    # Check every JSON file in samples/; writes nothing
    .\tools\mongo-json-importer\import.ps1 -DryRun

    # Import every JSON file in samples/
    .\tools\mongo-json-importer\import.ps1

    # Check or import one specific JSON file
    .\tools\mongo-json-importer\import.ps1 -File .\tools\mongo-json-importer\samples\example.json -DryRun
    .\tools\mongo-json-importer\import.ps1 -File .\tools\mongo-json-importer\samples\example.json

When -File is omitted, all *.json files directly inside tools/mongo-json-importer/samples/ are loaded in filename order. Every input file is parsed and preflighted before the first write.

## Input format

Each file contains a top-level collections object. Each property name is a MongoDB collection name and each value is an array of documents:

    {
      "metadata": { "name": "team-samples", "version": 1 },
      "collections": {
        "users": [{ "_id": "user-001", "name": "Alice" }],
        "products": [{ "_id": "product-001", "name": "Keyboard", "price": 100 }]
      }
    }

- metadata is optional and ignored.
- collections is required.
- Collection names are dynamic; no application domain is assumed.
- Every document must have a non-null _id. IDs are never generated automatically.
- MongoDB Extended JSON supports ObjectId, Date, Decimal128, and binary values.
- The importer validates the JSON envelope, document shape, and duplicate input IDs. It does not validate domain schemas or cross-collection references.

## Configuration

Importer-specific settings live in tools/mongo-json-importer/.env. Create it by copying tools/mongo-json-importer/.env.example. Shared connection settings remain in the repository root .env: MONGO_USER, MONGO_PASS, and APP_DB. The importer-local file can override the URI or target database.

| Variable | Default | Purpose |
|---|---|---|
| MONGO_IMPORT_URI | derived from root MONGO_USER / MONGO_PASS | Optional URI override reachable from Docker network |
| MONGO_IMPORT_DATABASE | root APP_DB | Target database |
| MONGO_IMPORT_CONFLICT_POLICY | skip | skip, fail, or replace |
| MONGO_IMPORT_BATCH_SIZE | 500 | Documents per bulk-write batch |
| MONGO_IMPORT_CONTINUE_ON_ERROR | false | Continue after a failed batch |
| MONGO_IMPORT_REQUIRE_CONFIRMATION | true | Require typing IMPORT before writes |

The default URI connects to the local Compose service mongodb. An override URI must be reachable from a temporary container attached to the Compose network.

## Conflict policies

- skip: keep existing documents with matching _id; insert only missing documents.
- fail: preflight all input and abort before writing if any _id already exists.
- replace: replace the entire existing document; old fields missing from the input are removed.

replace is destructive. Use -DryRun first and inspect the report.

## Safety and limitations

- -DryRun always performs read-only preflight and never writes.
- The tool refuses system databases and database names that look like production.
- All input files are preflighted before writes. A fail conflict therefore prevents all writes.
- MongoDB writes are batched, not transactional. If a later batch fails, earlier successful batches remain.
- MONGO_IMPORT_CONTINUE_ON_ERROR=true attempts later batches after a failed batch; it does not roll back successful writes.
- The importer does not infer dependency order or enforce foreign-key-like relationships. If documents depend on one another, order collection properties in the JSON accordingly.
- Input files must be inside the repository because the repository is mounted read-only into the temporary mongosh container.
- The tool starts only the mongodb Docker Compose service; it does not start the backend or Mongo Express.

## Troubleshooting

- Missing root .env: configure the project's MongoDB credentials. Missing importer .env: copy tools/mongo-json-importer/.env.example to tools/mongo-json-importer/.env.
- Docker unavailable: start Docker Desktop and retry.
- Empty samples/: add one or more JSON files or pass -File.
- Invalid JSON, missing collections, or missing _id: fix the input file before retrying.
- Import error: inspect the report. Earlier successful batches are not rolled back.
