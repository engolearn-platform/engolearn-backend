# A1 café sample pack — first verification

This pack intentionally contains **one A1 topic** so the team can verify the real MongoDB → backend → frontend path before authoring more topics.

## Scope

- 1 topic: **At the Café: My First Order**
- 5 published item versions, in learning order
- 15 vocabulary entries, with examples, collocations, quick checks and Vietnamese notes
- 10 expression groups with 20 example patterns
- 30 formal quiz questions: 3 FILL_BLANK and 3 SITUATIONAL_CHOICE questions per item

## Files

- `a1-cafe-topic.json`: content grouped by the four collections used by the current backend.
- `import-a1-cafe-topic.mongosh.js`: a local-only MongoDB Shell importer. It is read-only by default, checks IDs and relationships, refuses overwrites, and writes the topic document last so incomplete imports are not discoverable.
- `docs/topic/sample-data-plan.md`: concise scope and known blockers.

## Important compatibility notes

The repository's current BE models/VOs do not exactly mirror the illustrative schema v1. In particular, the current content models write root `_id` values as BSON ObjectIds but keep reference fields such as `latest_published_version`, `topic_version_id`, and `item_version_ids` as strings. This pack follows the current branch's model/reader shape to make the local catalog path verifiable; reconcile this drift before turning the pack into a reusable standard.

The business rules require UK and US pronunciation audio for publish-ready vocabulary. No confirmed project-hosted media URLs were found in the inspected sources, so the pack deliberately uses `null` rather than inventing URLs. By default, the importer blocks writes while this media is missing. An explicit override exists only for a **local UI smoke test**; data imported with the override is not business-publish-ready.

## Run on Windows PowerShell

Run from the repository root. Set a local connection URI and the exact database name; do not use a production URI.

```powershell
$env:TOPIC_SAMPLE_MONGO_URI = "mongodb://localhost:27017"
$env:TOPIC_SAMPLE_DATABASE = "engolearn_db"

# Read-only preflight (default)
mongosh --nodb --file tools/topic-sample-data/import-a1-cafe-topic.mongosh.js
```

For a **local UI preview only**, the missing-audio warning must be explicitly acknowledged:

```powershell
$env:TOPIC_SAMPLE_APPLY = "1"
$env:TOPIC_SAMPLE_ALLOW_MISSING_VOCAB_AUDIO_FOR_LOCAL_PREVIEW = "1"
mongosh --nodb --file tools/topic-sample-data/import-a1-cafe-topic.mongosh.js

Remove-Item Env:TOPIC_SAMPLE_APPLY
Remove-Item Env:TOPIC_SAMPLE_ALLOW_MISSING_VOCAB_AUDIO_FOR_LOCAL_PREVIEW
```

The importer refuses non-local hosts, system/production-looking database names, and conflicting IDs. It never drops collections, deletes data, or overwrites existing documents. An interrupted pack can be resumed only when existing seed IDs still match this file exactly.

**Run the import before starting the app with the `dev` profile.** The current `DevTopicSeedRunner` inserts placeholder topics when `topics` is empty; importing this topic first makes that runner see a non-empty collection and skip its placeholder seed.

After import, verify the topic via `GET /api/v1/topics?level=A1` and `GET /api/v1/topics/<topicId>`. Start/learn/quiz checks depend on which learning endpoints are currently implemented in the checked-out branch; do not count a missing endpoint as a sample-data failure.
