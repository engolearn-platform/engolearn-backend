# Topic Management F05–F08 verification

## Scope and baseline

The frontend and backend changes are in their original checkouts on `topic-management`, based on local `dev` at `0e9adee` and `f4b1ba9`, respectively. The frontend Speaking with AI README edit was stashed separately at `f866870`. No additional worktree was created. The local backend `feature/topic-management-api` ref at `20404da`, the F05 plan and updated design, and business rules F05–F10 were used as references. The fetch attempts could not resolve GitHub. No draft changes were merged or cherry-picked.

## API and UI coverage

| Slice | Before on `dev` | After on `topic-management` |
| --- | --- | --- |
| F05 Topic list | `PARTIAL`. FE used the management URL. BE `dev` lacked the route. The local draft had a paginated GET. | `EXISTING`. Protected GET serves combined filters, metadata, counts and paging from MongoDB. Read-only list has explicit empty and error states. |
| F05 Topic detail | `INCONSISTENT`. FE edit could load a fixture after a failed GET. The draft provided basic metadata only. | `EXISTING`. Read-only details use a protected GET for persisted metadata, release fields and F05 aggregates. Draft nulls remain explicit. |
| F06 Item list | `MISSING`. UI existed but its update snapshot supplied an empty list. Neither backend baseline offered this GET. | `EXISTING`. Protected topic-scoped GET lists stable Item IDs and selected versions in stored Item order. |
| F07 Context, Vocabulary, Expressions and Quiz | `MISSING`. Four editors used fixture content. Learner DTOs did not meet the management contract. | `EXISTING`. The four read tabs use management Item detail and expose persisted resources, quiz answers and available media. |
| F08 Read a saved draft | `MISSING`. Item assets and version metadata were unavailable in management. | `EXISTING`. Both Item paths apply the same management selector. Missing data is displayed as missing. |

| UI source | Fields |
| --- | --- |
| `API_BACKED` | Topic and Item metadata, selected version and release metadata, context and dialogue, vocabulary, expressions, quizzes and stored answer/media fields. |
| `DERIVED` | F05 counters, counted Item total and summed duration, as defined in the API contract. |
| `FE_FIXTURE` | Presentation labels and helper/empty-state copy, listed in the frontend fixture inventory. |
| `UNSUPPORTED` | Unverified quality and completeness scores or media fields absent from the persistence model. |

## Ownership

| Work | Owner |
| --- | --- |
| Source and local draft discovery | `context_reader` |
| BE routing, RBAC, list and version queries, tests and integration | Orchestrator |
| BE Item DTOs, resource mapping and mapper tests | `resources` |
| FE navigation, read-only UI, fixtures inventory and tests | `frontend` |
| Backend review and browser verification | `review`, `browser_check` |

## Backend verification

| Check | Observed result |
| --- | --- |
| `mvnw.cmd -Dtest=TopicManagement*Test,ArchitectureTest test` | 17 passed with Docker available. This includes HTTP and method authorization plus MongoDB integration. |
| `mvnw.cmd verify` | 873 tests reported, with 7 failures, 61 errors and 29 skipped. The 8 management tests and 9 architecture tests passed within the full run. |

The full-run failures are in the application startup, Placement, Subscription, and learner Topic suites. Observed examples include quota index bootstrap against MongoDB on port 1, test database authorization on port 27017, missing `DevPurchaseResetService`, and learner requests that expected success but received 401. These suites are outside the management change. Their failures were not individually reproduced on `dev`.

The management integration suite uses a disposable MongoDB replica set. It checks literal case-insensitive filtering, counters, incomplete drafts, Item status and selected version, rejection of mismatched Topic and Item IDs, distinct persisted resources for two Items, quiz answers, and equality of the Topic/Item collections before and after reads. The web-slice test covers management HTTP 401/403/404 and Spring method authorization.

## Frontend verification

| Check | Observed result |
| --- | --- |
| `npm run lint` | Exit 0, 72 warnings. |
| `npm run build` | Exit 0. Vite reported a chunk larger than 500 kB. |
| `npm run test:topic-management-read` | 5 passed. Includes URL scoping and encoding, empty result, persisted resource and answer payloads, HTTP errors, and network rejection. |
| `npm run test:auth` | 21 passed. |
| `npm run test:topic` | 31 passed and 2 failed in the learner API contract tests. The operation-count assertion expected 17 and received 20. A documented success example failed schema validation for missing `entries`. |

## Browser verification

Microsoft Edge ran the final frontend build against the packaged backend and a disposable MongoDB replica set seeded with the repository's five-Item `Daily Routine` sample. The authenticated Content Manager reached the Topic list, Topic details, Item list and the Context, Vocabulary, Expressions and Quiz tabs. A resource deep link survived refresh and browser Back navigation. An anonymous list request returned 401. The Quiz screenshot shows the persisted correct answer and explanation. Seven screenshots are under `e2e-artifacts/topic-management-read` at the workspace root.

## Known limits

- Management F05 retains the agreed draft/published pointer classification. The detail response separately exposes the stored release status. The management Item selection is independent of the learner release resolution.
- No trusted source exists for authoring quality or completeness scores in these endpoints. The frontend fixture inventory documents which presentation text remains static.
- Media paths are rendered when the client can use the stored URL. A filename without a resolvable base is displayed as stored metadata.
