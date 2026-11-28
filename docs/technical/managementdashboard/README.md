# Management Dashboard Summary API

The read-only summary endpoint is `GET /api/management/dashboard/summary`. It returns the standard `{ data, timestamp }` response and sends `Cache-Control: no-store`.

The minimum role is `CONTENT_MANAGER`; `ADMIN` inherits access. Guest requests receive `401`, and authenticated `USER` requests receive `403`. Content managers receive `data.content` only. Administrators also receive `data.administration`. The administration providers are not called for content managers.

`content` contains whole-dataset counters for topics, grammar, passages, and Learning Path questions. `administration` contains non-deleted user accounts, unique users with an effective paid Premium period at one instant, and Placement Test set/question totals. Empty collections produce zero. Provider errors propagate through the existing error contract rather than becoming zeroes.

The endpoint orchestrates owner-owned public contracts. Topic, Grammar, Passage, and question totals reuse their management counters; other totals use owner repository count queries. Subscription evaluates effective periods with an inclusive start and exclusive end, rejects overlapping active periods, and excludes Trial, expired, and revoked periods.

## Response data

```json
{
  "content": {
    "topics": { "total": 0, "published": 0, "draft": 0, "unpublished": 0 },
    "grammar": { "total": 0, "published": 0, "draft": 0, "inProgress": 0, "archived": 0 },
    "passages": { "total": 0, "active": 0, "draft": 0, "retired": 0 },
    "learningPathQuestions": { "total": 0 }
  },
  "administration": {
    "users": { "total": 0 },
    "subscription": { "activePremium": 0 },
    "placement": { "testSets": 0, "questions": 0 }
  }
}
```

The actual response wraps this `data` object with `timestamp`. `administration` is omitted for CONTENT_MANAGER.
