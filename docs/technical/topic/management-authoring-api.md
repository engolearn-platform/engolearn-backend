# Topic Management Authoring API

F05 / TPL-UC-010 and F08 / BR-F8.1–BR-F8.7 extend the [management read API](management-read-api.md). Write endpoints require the same Content Manager authority, inherited by Admin. The Topic catalog is shared by managers. Success uses `{ data, timestamp }` and failure uses ProblemDetail.

| Method and path | Result |
| --- | --- |
| `POST /api/management/topics` | `201`, `Location: /api/management/topics/{topicId}`, and the persisted Topic detail at `revision: 0`. Only `titleEn` and `titleVi` are required. |
| `PATCH /api/management/topics/{topicId}` | `200` and the saved Topic detail with the new `revision`. Send the integer `revision` from the latest detail response. |
| `POST /api/management/topics/{topicId}/publish` | Initial Topic Learning release. Send `{revision}`; success is `200` with the committed TopicVersion, ordered ItemVersion manifest summary and release aggregates. |
| `POST /api/management/topics/{topicId}/publish` | On a released Topic, publishes all valid pending MAJOR/new Item drafts in one atomic release. Success includes `previousTopicVersionId` and `changes` (old/new ItemVersion IDs and `NEW`/`MAJOR` kind); `topicVersionId` is the release idempotency key. |

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
| `POST /{itemId}/working-draft` | `{revision, baseItemVersionId}` | `200`, opens the single eligible MAJOR working copy from the exact current published version, preserving Item identity and copying all content. |
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

Item detail reads default to the current management working selection. Add `?view=published` to resolve the exact ItemVersion from the current TopicVersion manifest; this is the management view of released content while a draft is being edited.

Expression purpose types are `BASIC`, `COMMON`, and `PLACEHOLDER`. Sentence labels are `COMMON`, `POLITE`, and `SLOT_HINT`. A sentence belongs to the purpose whose `examples` contains it. Quiz option correctness is represented by `isCorrect` and its option label by `correct`. FILL_BLANK stores its word bank as options and its completed English sentence in `sentence.textEn`. A complete question includes one shared `explanation`. Learner question reads omit the stored correctness and answer payloads.

## Initial publish (F09)

The initial publish command is available to Content Managers, with Admin inheriting the same authority. It accepts the latest saved Topic `revision`; the command recalculates readiness against the transactional saved candidate and advances the Topic revision once on success. A readiness response fetched earlier does not authorize publication.

The candidate is the current management selection in saved Item order. Only visible Items are included; hidden Items are omitted from validation, the release manifest, count and duration. A first release requires 5–20 visible Items, each still an unreleased first draft at Item version `1`. It creates one TopicVersion at version `1`, promotes the selected ItemVersion documents in place, and stores their IDs in the ordered `itemVersionIds` manifest. It does not create duplicate ItemVersions or write learner attempts/progress.

Success uses the standard `{ data, timestamp }` envelope:

```json
{
  "data": {
    "topicId": "<topicId>",
    "revision": 12,
    "topicVersionId": "<topicVersionId>",
    "version": 1,
    "items": [
      {"topicItemId": "<topicItemId>", "itemVersionId": "<itemVersionId>", "version": 1,
       "order": 1, "title": "At the station", "durationMinutes": 5}
    ],
    "itemCount": 5,
    "durationMinutes": 25,
    "publishedAt": "2026-11-26T03:00:00Z"
  },
  "timestamp": "2026-11-26T03:00:00Z"
}
```

If the saved candidate is incomplete, the command returns `422 PUBLISH_VALIDATION_FAILED` with `violations: [{path, code, message}]` on ProblemDetail. This is the same ordered violation list returned by readiness; no release, Item status, pointer, aggregate or revision writes commit. Stale revisions and a Topic that already has a release return `409 CONFLICT`; reload the saved Topic and do not blindly retry an old revision. Unknown Topics return `404 RESOURCE_NOT_FOUND`; missing authentication or role returns `401` or `403`.

For a fresh initial release, the cover must be a managed Topic-owned image. Both `audioEnUrl` and `audioUsUrl` are required managed Topic-owned audio for each vocabulary entry; every example and expression also requires managed Topic-owned audio. Present managed references on optional fields must still belong to this Topic and have the expected media kind. Unverified external URLs are not accepted for this fresh release path. Context media, optional vocabulary extras, and quiz media may be absent. Speaking Profile readiness is independent and is not checked or changed by Topic Learning publish.

## Publish readiness (F08/F09)

`GET /api/management/topics/{topicId}/readiness` returns `{ data: { ready, violations: [{ path, code, message }] }, timestamp }`. It validates the current saved content when requested, excludes hidden Items, and reports field paths such as `items[itemId].context.scenarioDescription`. Completeness checks run separately from draft persistence. This read performs zero writes and creates zero versions. For the initial publish media policy, it applies the same managed ownership, kind and required-track checks as the command.

Legacy media URLs have no recorded file type or byte count. They are not accepted for the fresh initial publish path until their ownership and kind can be proven. New upload file signatures and sizes are checked by MediaManagement.

## Published Item MINOR correction (F10)

`PATCH /api/management/topics/{topicId}/items/{itemId}/minor` applies one correction directly to a current published ItemVersion. It requires Content Manager authority (Admin inherits it), the latest Topic `revision`, and the current target `itemVersionId`. Submit exactly one non-null section using the same typed section shape returned by management detail: `context`, `vocabularies`, `expressions`, or `quizzes`. Context cannot be cleared by this command; send a non-null complete section. A missing or stale Topic revision and a wrong, stale, or non-current ItemVersion return `409 CONFLICT`. Pending DRAFT working copies block MINOR with the same conflict response.

Only explicit leaf changes are written, preserving unrepresented legacy fields. The supported leaf corrections are Vietnamese dialogue text and audio; stable Dictionary vocabulary spelling (only when every entry has a nonblank unchanged `vocabId` and `senseId`), Vietnamese meaning/description, phonetic spelling, audio, and Vietnamese sample text/audio; Vietnamese expression purpose/example text, usage notes and audio; and quiz explanations plus question/sentence audio. List sizes, order, all identities, labels/types/groups, prompts, answer keys, choices, word banks, grading maps and all other fields must match the current section exactly. Reorders and any ambiguous or structural difference return `400` with `code: MINOR_REQUIRES_MAJOR`. Changed audio must be a managed audio reference owned by this Topic; unchanged historical external media is retained without revalidation.

Success returns the usual `{ data: { revision, item }, timestamp }` response. The Topic revision advances once. The ItemVersion ID, business version, original release attribution and published timestamp, TopicVersion manifest, learner progress and attempts are preserved. MINOR creates no release or notification. Example request:

```json
{"revision":12,"itemVersionId":"<currentItemVersionId>","vocabularies":[{"vocabId":"<id>","senseId":"<sense>","snapshot":{"word":"<corrected spelling>"},"samples":[],"collocations":[],"orderIndex":1,"tips":"<existing value>"}]}
```

## MAJOR working copies and batch release (F10)

For a current visible published Item, `POST /api/management/topics/{topicId}/items/{itemId}/working-draft` accepts `{revision, baseItemVersionId}`. The base must be the exact ItemVersion in the current release manifest. The operation advances Topic revision once, preserves the stable TopicItem ID, and copies the complete content into a DRAFT with `changeType: MAJOR`, `previousItemVersionId` set to the base, and the next version number derived from persisted history. Repeating the request with the current revision and same base reopens the same eligible draft without a write. Unrelated, stale or ambiguous drafts and stale revisions/bases return `409`.

On a published Topic, readiness and `POST .../{topicId}/publish` evaluate the combined manifest for visibility and Item count. Unchanged released Items and unchanged sections of a MAJOR draft carry forward as saved. Changed MAJOR sections are validated against publish rules. Each inherited external audio value (or already missing required audio value) can carry forward within the same Item and field shape up to its previous count, including after reordering. Added or replaced audio and all new Item drafts must meet the managed-media rules. An existing legacy cover can carry forward; a replacement cover on a released Topic must be a managed Topic-owned image. Existing order and stable IDs remain fixed; new Items append. Hidden historically released ItemVersion IDs remain in their original manifest positions to preserve the F06 Show behavior, but are not validated, promoted or counted; never-published hidden drafts are omitted. Changed MAJOR/new drafts advance in the release; unchanged published IDs are reused. An unchanged working copy is retired only when it is a provisional DRAFT with no publication marker or release-manifest reference. If the batch contains only unchanged copies, it returns `409` with code `NO_EFFECTIVE_CHANGES` and leaves those drafts available to reopen. TopicVersion version is the maximum persisted release version plus one. Previous release and ItemVersion documents are retained.

Success contains `previousTopicVersionId`, committed `topicVersionId`, and `changes` entries with each changed/new stable Item ID, previous and new ItemVersion IDs, version and `MAJOR`/`NEW` type. Consumers can use the committed TopicVersion ID as an idempotency key. No recipient, channel or delivery mechanism is defined by this manager API; learner notification delivery remains an owner-confirmed downstream handoff.

## Topic media (F08)

Uploads are Topic-owned and use MediaManagement's protected storage. `POST /api/management/topics/{topicId}/media/{kind}` accepts multipart field `file`, where `kind` is `image` or `audio`; files are limited to 5 MiB and checked against both the declared MIME type and file signature. Success returns `data.reference` in the form `topic-media:{topicId}:{mediaId}`. Store that reference in the relevant authoring field; the backend records the media ID and kind on the Topic without advancing the content revision. Subsequent cover/context writes still require the current Topic revision.

`GET /api/management/topics/{topicId}/media/{mediaId}` requires Content Manager access, verifies Topic ownership, and redirects to the short-lived protected delivery URL with `Cache-Control: no-store`. Use this endpoint to load protected assets in management views. Published Topic cover reads resolve a valid Topic-owned image reference after the published Topic has passed browse visibility checks. Existing non-marker cover URLs continue to pass through unchanged. Invalid uploads return `400 MEDIA_UPLOAD_REJECTED`; unavailable storage returns `503 MEDIA_STORAGE_UNAVAILABLE`; missing or cross-Topic references return `404` on protected lookup and validation errors when attached.

## Unpublish / republish (F10)

Management Topic lists accept the exact `status` values `draft`, `published`, and `unpublished`. An unpublished Topic retains its `latestPublishedVersion` pointer and its TopicVersion and ItemVersion documents; legacy Topics with a release pointer and no `publication_suspended` field remain published. List counters include `total`, `published`, `draft`, and `unpublished`.

`POST /api/management/topics/{topicId}/unpublish` requires Content Manager authority and `{ "revision": n }`. The command verifies the current owned PUBLISHED release, then atomically sets the Manager publication marker and increments the Topic revision. Success returns `{topicId, revision, status: "unpublished", latestPublishedVersion}`. A stale revision, missing release, already-unpublished Topic, or concurrent change returns `409`; no release, Item version, media reference, progress or attempt is deleted or rewritten.

Republish uses the existing `POST /api/management/topics/{topicId}/publish` command and the same saved-candidate readiness checks. When a suspended Topic has no effective Item changes, the endpoint reuses its existing release ID and version and clears the marker atomically; it creates no TopicVersion. When it has valid NEW or changed MAJOR candidates, the existing batch command writes one successor TopicVersion, preserves the prior release and manifest, and clears the marker in the same Topic compare-and-set. Invalid candidates or transaction failures leave the Topic unpublished. The marker describes Manager/system publication state; learner discoverability and continuation remain a downstream integration handoff.