# Micro-skill catalog

The `microskill` feature owns `micro_skills` and bilingual definitions shared through
`microskill.contract.MicroSkillLookup`. Learning Path and
[Assessment](../assessment.md#studio-micro-skill-and-workbook-import) consume this catalog.

## Data and identity

`MicroSkill` extends `BaseDocument`: inherited string `_id`, `created_at`, `updated_at`.
Fields: `code`, `canonical_name`, `display_name_vi`, `description_vi`, `skill`
(`READING`/`LISTENING`), `status` (`ACTIVE`/`INACTIVE`), `display_order`, `revision`,
`referenced`, `created_by`, `updated_by`. Code and canonical name have unique indexes;
ID, code, canonical name and category are immutable through the update API. Display labels,
description, status and order can change without changing consumer identity.

Indexes are declared on the model and initialized by `MicroSkillMongoIndexes`.
`engolearn.microskill.indexes.enabled=false` disables initialization when infrastructure
manages the same unique indexes separately. Imports must preserve these invariants;
the generic JSON importer does not validate domain constraints.

The ten seed IDs equal their stable codes (`MS-L-GIST`, `MS-L-SPECIFIC`, `MS-L-DETAIL`,
`MS-L-INFERENCE`, `MS-L-ATTITUDE`, `MS-R-GIST`, `MS-R-SPECIFIC`, `MS-R-DETAIL`,
`MS-R-VOCABULARY`, `MS-R-INFERENCE`). New API-created IDs are opaque `MS-<UUID>` values;
consumers must not infer code or category from an ID.

## HTTP API

Success: `ApiResponse<T>`; failure: `ProblemDetail`. Read endpoints require USER
(including inherited CONTENT_MANAGER/ADMIN). Management routes require CONTENT_MANAGER
(including ADMIN) at HTTP and mutation service boundaries. Actor comes from the session.

| Method | Path | Behavior |
| --- | --- | --- |
| GET | `/api/micro-skills` | All definitions in display order, including inactive definitions for historical labels |
| GET | `/api/micro-skills/{id}` | Definition or 404 |
| GET | `/api/management/micro-skills` | Management list |
| GET | `/api/management/micro-skills/{id}` | Management detail |
| POST | `/api/management/micro-skills` | Create; 201 |
| PUT | `/api/management/micro-skills/{id}` | Replace editable metadata with expected revision |
| DELETE | `/api/management/micro-skills/{id}?revision=N` | Delete only a never-retained definition at that revision |

Create body:

```json
{
  "code": "CUSTOM_READING_SKILL",
  "canonicalName": "Custom reading skill",
  "displayNameVi": "Ky nang doc moi",
  "descriptionVi": "Mo ta ky nang",
  "skill": "READING",
  "status": "ACTIVE",
  "displayOrder": 11
}
```

Update body contains `revision`, `displayNameVi`, `descriptionVi`, `status`,
`displayOrder`. It does not change identity/category. Response contains ID, all metadata,
revision, reference protection and creation/update timestamps. List response: `{items: [...]}`.
Missing records return 404; duplicate code/name, stale update/delete and protected deletion
return 409; malformed input returns 400; unauthenticated/insufficient role returns 401/403.

## Consumer contract and deletion

`list`, `findById`, `findByCanonicalName` are read-only and include inactive definitions.
An absent lookup is `Optional.empty()`. `list` is sorted by display order then code.
Returned DTOs and lists are immutable. No consumer reads this feature's repository or collection.

Before persisting a new reference, consumers call `retainActive(canonicalName)`.
It atomically checks ACTIVE and sets `referenced=true`; absent means unavailable.
The retention write competes with deletion on the same document. When the caller has a
Mongo transaction, it participates in that transaction. Retention is permanent: it is not
released automatically if a consumer later deletes its own data or an operation fails
outside a transaction. This conservative protection preserves historical identities.
Referenced definitions are deactivated rather than physically deleted. The ten seeds
start referenced because existing Assessment/Learning Path data already uses these identities.
Do not clear protection or remove/recreate seed IDs through an importer.

Deactivation prevents subsequent eligibility/retention decisions; it does not rewrite
already-created paths, snapshots, results or work in progress. Concurrent path creation
uses the active definition observed at successful retention, without a cross-module lock.

## Learning Path integration

The five Assessment-code-to-canonical mappings stay in `LearningPathAssessmentMapper`.
Recommendation reads the catalog in addition to existing weak-score, occupied-skill,
user-level and 30-question checks. A missing weak-skill definition or category mismatch
returns 409; inactive definitions are excluded. There are still only five approved mappings,
even though the catalog contains ten definitions. Existing-source idempotency is preserved.

New paths retain their definitions and add `micro_skill_id` to the path's micro-skill
baseline entries. Existing canonical names, question selection, coverage keys, uniqueness
indexes and snapshot content are unchanged. Old paths without catalog IDs remain readable.
No migration of old snapshots or Assessment data is required for this compatibility stage.

Question creation/edit/copy validates and retains the selected active definition inside
its existing Mongo transaction. Readiness checks catalog category/status; publish/replace
retains again in the existing publication transaction. Retirement remains possible even
if the catalog definition is inactive.

Recommendation, list/search, detail, start/resume, day result and management question
list/detail responses add `microSkillCatalog: MicroSkillDto[]`. This contains the catalog
once per response, including inactive definitions. Existing canonical fields and nested
payloads are preserved. UI joins on `canonicalName`, displays `displayNameVi`, and falls
back to its existing canonical when no label is available. This is current catalog metadata,
not a historical snapshot. No frontend files are changed in this backend delivery.

## Import and handoff

Dataset: [`micro-skills.json`](../../../tools/mongo-json-importer/samples/micro-skills.json).
From repository root, after configuring the importer for the intended database:

```powershell
.\tools\mongo-json-importer\import.ps1 -File .\tools\mongo-json-importer\samples\micro-skills.json -DryRun
.\tools\mongo-json-importer\import.ps1 -File .\tools\mongo-json-importer\samples\micro-skills.json
```

Use `skip` to preserve catalog edits on reimport. A deliberate `replace` overwrites whole
documents, including labels/revisions; see the [importer](../../../tools/mongo-json-importer/README.md).
String seed IDs contain non-hex prefixes and remain strings under default importer conversion.
Application startup creates indexes, not seed records. Import the catalog before using
new recommendation/authoring flows. Importing this file does not migrate other collections.

Assessment uses the five approved PFC mappings and the same canonical names for
retention and display. Published and historical identities remain compatible.
Administration of learner Learning Paths remains future work.
