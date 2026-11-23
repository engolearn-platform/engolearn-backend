# Grammar administration

The administration API implements GRM-UC-005 through GRM-UC-013. Its business source is `docs/usecase_spec/grammar-learning` in the business documentation repository. All routes under `/api/v1/grammar/admin/topics` require the existing Content Manager or Admin role.

| Resource relative to `/topics` | Operations |
| --- | --- |
| `/` | List with `search`, `level`, `status`, zero-based `page`, and `size`. Create a topic. |
| `/{id}` | Get, update, or delete a topic. |
| `/{id}/checklist` | Read the topic publication checklist. |
| `/{id}/publish`, `/{id}/archive`, `/{id}/restore` | Change the topic lifecycle state. |
| `/{id}/lessons` | Create a lesson using its basic information. |
| `/{id}/lessons/{lessonId}` | Get, update, or delete a lesson. `/preview` reads the same working content. |
| `/{id}/lessons/{lessonId}/checklist` | Read the lesson readiness checklist. |
| `/{id}/lessons/{lessonId}/ready` | Mark the lesson ready. Include `confirmPublish: true` to publish a new lesson in a published topic. |
| `/{id}/lessons/{lessonId}/apply`, `/{id}/lessons/{lessonId}/discard-draft` | Apply or discard a published lesson’s editing draft. |

Update and lifecycle requests send the version returned by the resource. Delete sends the version as a query parameter. Lesson creation sends the current parent topic version. The topic version advances when any lesson changes, so publication uses the same topic and lesson revisions that were reviewed. Versions of rows shifted by reordering advance as well. A stale version returns `409` and a failed checklist returns `422` with the individual checks. Successful deletes return `204`.

Topic status `IN_PROGRESS` in list and detail responses represents a draft topic with at least one ready lesson. Lessons expose their state and readiness separately. Archived entities have no active order. Individual lesson archives stay archived after a topic is restored.

The feature uses the existing MongoDB transaction manager. Run writes against the project’s replica set with `engolearn.mongodb.transactions.enabled=true`. An ordering counter serializes topic insertions and position changes. Each topic stores its lessons and their working and current published content. Immutable published versions and actor-attributed audit entries use separate collections and commit with their corresponding topic changes.

## Learning API
The learner API implements GRM-UC-001 through GRM-UC-004. Only published topics and lessons are shown. Each request uses the authenticated user ID from the shared security provider.

| Path relative to `/api/v1/grammar` | Operations |
| --- | --- |
| `/roadmap` | GET returns four CEFR levels, completion counts, and any newly unlocked levels. |
| `/topics/{topicId}` | GET returns the topic and ordered lesson summaries including locks and update notices. |
| `/topics/{topicId}/lessons/{lessonId}` | GET returns the published theory. It does not record a view. |
| `/topics/{topicId}/lessons/{lessonId}/seen` | POST acknowledges the published content version after the FE displays the theory. |
| `/topics/{topicId}/lessons/{lessonId}/practice` | POST starts or resumes Practice. Set `restart: true` to abandon a still-active session. |
| `/practice/{sessionId}` | GET returns the current question or stored result for the session owner. |
| `/practice/{sessionId}/answers` | POST accepts the request ID, current question ID and answer. It returns the saved feedback and session. |

A session uses the immutable published revision and expires after 30 minutes of inactivity. Question and option IDs are scoped to the session. Feedback is exposed after an answer is recorded and is read from that revision. Duplicate submissions return the first recorded feedback. A score of at least three out of five marks a published lesson complete when it is still available. Completion, score, the final session and level unlocks are committed together. Restarting or expiring a session does not count as completing an attempt.

A passing attempt that finishes while archived contributes to current progress only after republication. The historical result keeps its recorded archive state. For a result finalized while published, the suggested next lesson follows the current published order and access.

Levels unlocked through learning are permanent. A1 is the initial level while the placement-level contract remains deferred in OQ-GRM-F1-01. Run with the existing `dev` profile and a MongoDB replica set for writes.

MongoDB stores learning data in `grammar_lesson_progress`, `grammar_level_unlocks`, and `grammar_practice_sessions`. `grammar_learner_locks` serializes related writes for the same learner. Practice reads the existing `grammar_published_revisions`.
