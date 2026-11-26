# Topic Management Authoring API

F05 / TPL-UC-010 and F08 / BR-F8.1–BR-F8.7 extend the [management read API](management-read-api.md). Write endpoints require the same Content Manager authority, inherited by Admin. The Topic catalog is shared by managers. Success uses `{ data, timestamp }` and failure uses ProblemDetail.

| Method and path | Result |
| --- | --- |
| `POST /api/management/topics` | `201`, `Location: /api/management/topics/{topicId}`, and the persisted Topic detail at `revision: 0`. Only `titleEn` and `titleVi` are required. |
| `PATCH /api/management/topics/{topicId}` | `200` and the saved Topic detail with the new `revision`. Send the integer `revision` from the latest detail response. |

Both accept editable metadata `titleEn`, `titleVi`, `cefr` (`A1`–`B2`), `category` (`DINING`, `TRAVEL`, `BUSINESS`, `DAILY`, `HEALTH`), `description`, `coverUrl`, and `tags` (array of strings). The current authoring contract adds `tags` and `revision` to the earlier F05 r7 design. PATCH retains omitted values and accepts explicit null for optional metadata. The API rejects unknown fields, duration, and version pointers. Existing documents without a revision read as `0`.

```json
{"revision": 0, "category": "DINING", "description": null, "tags": ["menu"]}
```

MongoDB applies PATCH with an atomic revision filter and field-level update. Published Topic metadata changes in place per BR-F5.6. Topic and Item Version documents retain their contents and release references. Topic hard delete has no business contract.

Invalid edits return `400 VALIDATION_ERROR` with `fieldErrors`. Sessions or roles yield `401` or `403`. Unknown IDs return `404 RESOURCE_NOT_FOUND`. A stale revision returns `409 CONFLICT`; reload detail and reconcile before retrying. Save Draft allows incomplete content under BR-F8.2. The cover field currently accepts an existing reference.

## Item authoring (F06)

All paths below are relative to `/api/management/topics/{topicId}/items`. The response `data` contains `{revision, items}`. Send the parent Topic's revision on every write and retain the revision from the returned snapshot.

| Method and path | Input | Result |
| --- | --- | --- |
| `POST` | `{revision, title, description?, durationMinutes?}` | `201`, appends a new draft with its Item and version IDs. |
| `PATCH /{itemId}` | `{revision, title?, description?, durationMinutes?}` | `200`, updates the eligible working draft. Optional metadata can be cleared with null. |
| `PATCH /order` | `{revision, itemIds}` | `200`, persists the complete order for a Topic still in draft. |
| `PATCH /{itemId}/visibility` | `{revision, hidden}` | `200`, toggles visibility when the draft or release can be identified safely. |
| `DELETE /{itemId}?revision=n` | Revision in the query | `200`, removes a proven unreferenced draft. |

MongoDB transactions apply the parent revision comparison and changes together. Deletion eligibility checks version history, release references, learner progress and learner attempts. `ITEM_DELETE_BLOCKED` identifies an ineligible delete and directs the caller to hide the Item. The visibility operation preserves version IDs and learner records. Working drafts can be appended to a published Topic in the existing order. Applying MINOR or publishing MAJOR modifications requires F09/F10.

## Learning resource drafts (F07)

Paths are relative to `/api/management/topics/{topicId}/items/{itemId}`. These calls return `{ data: { revision, item }, timestamp }` with the post-save working version.

| Method and path | Input |
| --- | --- |
| `PUT /context` | `{revision, context}`. Supply the Context representation from the detail API or send `context: null` to clear. The dialogue array expresses its order. |
| `PUT /vocabulary` | `{revision, vocabularies}`. Supply the ordered Vocabulary representations from the detail API. An empty array clears the section when existing expressions permit it. |
| `PUT /expressions` | `{revision, expressions}`. Same ordered blocks as detail. Purpose `groupType` and sentence `label` have separate values from legacy sentence `type`. An empty array clears the section. |
| `PUT /quizzes` | `{revision, quizzes}`. Same module/question representation as detail. New modules support `FILL_BLANK` and `SITUATIONAL_CHOICE`. An empty array clears the section. |

Both update one section on an eligible draft version in the parent revision transaction. Vocabulary entries preserve Dictionary IDs and snapshots. New Dictionary references must resolve through the Dictionary contract. A linked vocabulary reference must remain available to expressions in the same Item. Save Draft accepts missing publish-required content. Learner reads continue to use their publication pointers.

Expression purpose types are `BASIC`, `COMMON`, and `PLACEHOLDER`. Sentence labels are `COMMON`, `POLITE`, and `SLOT_HINT`. A sentence belongs to the purpose whose `examples` contains it. Quiz option correctness is represented by `isCorrect` and its option label by `correct`. FILL_BLANK stores its word bank as options and its completed English sentence in `sentence.textEn`. A complete question includes one shared `explanation`. Learner question reads omit the stored correctness and answer payloads.

## Publish readiness (F08/F09)

`GET /api/management/topics/{topicId}/readiness` returns `{ data: { ready, violations: [{ path, code, message }] }, timestamp }`. It validates the current saved content when requested, excludes hidden Items, and reports field paths such as `items[itemId].context.scenarioDescription`. Completeness checks run separately from draft persistence. This read performs zero writes and creates zero versions.

Legacy media URLs have no recorded file type or byte count. Readiness checks for the required references. New upload file signatures and sizes are checked by MediaManagement.

## Topic media (F08)

Uploads are Topic-owned and use MediaManagement's protected storage. `POST /api/management/topics/{topicId}/media/{kind}` accepts multipart field `file`, where `kind` is `image` or `audio`; files are limited to 5 MiB and checked against both the declared MIME type and file signature. Success returns `data.reference` in the form `topic-media:{topicId}:{mediaId}`. Store that reference in the relevant authoring field; the backend records the media ID and kind on the Topic without advancing the content revision. Subsequent cover/context writes still require the current Topic revision.

`GET /api/management/topics/{topicId}/media/{mediaId}` requires Content Manager access, verifies Topic ownership, and redirects to the short-lived protected delivery URL with `Cache-Control: no-store`. Use this endpoint to load protected assets in management views. Published Topic cover reads resolve a valid Topic-owned image reference after the published Topic has passed browse visibility checks. Existing non-marker cover URLs continue to pass through unchanged. Invalid uploads return `400 MEDIA_UPLOAD_REJECTED`; unavailable storage returns `503 MEDIA_STORAGE_UNAVAILABLE`; missing or cross-Topic references return `404` on protected lookup and validation errors when attached.
