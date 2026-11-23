# Topic Management Read API

**Business references** F05 / TPL-UC-019 and TPL-UC-010, F06 / TPL-UC-011, F07 / TPL-UC-012–015, F08 / TPL-UC-016, BR-M.5 and BR-M.7.

The API serves management content from MongoDB. Both the HTTP boundary and the service require the `CONTENT_MANAGER` authority. `ADMIN` inherits that authority. The current Topic model has no per-manager ownership field, so the managed set is the shared Topic collection. A missing or expired session returns `401`, a learner session returns `403`, and an unknown Topic or an Item outside the requested Topic returns `404`. Errors use `application/problem+json`. Invalid query parameters return `400` with `code: VALIDATION_ERROR` and `fieldErrors`. Successful responses use `{ data, timestamp }`.

## Endpoints

| Method and path | Response `data` |
| --- | --- |
| `GET /api/management/topics` | `{ items, page, size, totalElements, totalPages, counters }`. Each row carries `id`, `titleEn`, `titleVi`, `cefr`, `category`, `status`, `itemCount`, and `updatedAt`. |
| `GET /api/management/topics/{topicId}` | Topic identity and metadata, including bilingual titles, CEFR, category, description, cover URL, tags, order, dates, computed duration and Item count, stored publication pointers, referenced release metadata, and the `revision` for authoring. |
| `GET /api/management/topics/{topicId}/items` | Array of ordered Item summaries. Empty Topics return `[]`. |
| `GET /api/management/topics/{topicId}/items/{itemId}` | Item summary fields plus `context`, `vocabularies`, `expressions`, and `quizzes`. `itemId` is the stable `TopicItem` identity. |

The list accepts `search` (literal case-insensitive substring over both titles, up to 200 characters), `cefr` (`A1`–`B2`), `category` (`DINING`, `TRAVEL`, `BUSINESS`, `DAILY`, `HEALTH`), `status` (`draft` or `published`), `page` (zero-based, default `0`), and `size` (`1`–`100`, default `20`). Filters combine with AND. Results sort by `updatedAt` descending with ID as the tie-breaker. `counters` reports the unfiltered `total`, `published`, and `draft` counts.

## Publication and version semantics

- F05 retains its two-value Topic classification. A stored publication pointer classifies a Topic as `published`. Detail shows the referenced release's persisted `status` separately, along with `id`, `version`, `previousVersionId`, and `publishedAt`. A missing or foreign release yields null `publicationVersion`.
- F05 aggregates the highest numeric version per Topic and Item before excluding `HIDDEN`. `itemCount` counts the remaining records and `durationMinutes` sums their stored minutes. A null duration contributes zero. This follows the local F05 draft and the updated F05 design decision and is separate from the Item working-version selection.
- F06–F08 use `TopicItem` identities belonging to the requested Topic. A version referenced by the Topic release manifest provides the published baseline. The highest saved `DRAFT` or `HIDDEN` version at or above that baseline takes precedence for management reads. Higher `BETA`, `ARCHIVED`, and unreferenced `PUBLISHED` records do not supersede the baseline. With no usable version, the Item identity is returned with nullable metadata and empty resources. No learner version, release pointer, or progress is written by these reads.
- Item summaries carry `id`, `topicId`, `title`, `description`, `order`, `status`, `durationMinutes`, `versionId`, `version`, `topicVersionId`, `previousItemVersionId`, `changeType`, `createdAt`, `updatedAt`, and `publishedAt`. `status` is the selected version's stored status. The list sorts by `order` with nulls last, then by stable Item ID.
- Detail preserves stored resource array order and the embedded fields, including vocabulary snapshots, samples, collocations, quick checks, expression groups, quiz option correctness, stored answer data, explanations, and media references. Missing resource arrays return `[]`. Missing Context and nullable fields stay null. Stored media references are returned as-is.

## Scope

The four management endpoints perform reads and use dedicated HTTP DTOs. Learner endpoints retain their own release resolution and resource mapping. See the [authoring API](management-authoring-api.md) for mutations.

See the [coverage and verification report](management-read-verification.md) for the change baseline and test evidence.
