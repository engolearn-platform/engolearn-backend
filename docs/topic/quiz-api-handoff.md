# Bàn giao API quiz — Slice A (GET current quiz + POST answer)

## 1. Baseline

| Thành viên | Commit thực tế (HEAD) | Commit ghi trong design.md | Ghi chú |
|---|---|---|---|
| BE | `4934b88` | `4b36f15` | HEAD thực tế khác thiết kế; thiết kế là mô tả mục tiêu, không phải trạng thái code. |
| FE contract (`api.openapi.yaml`) | `9237832` | `a5d0b4a` | FE đang đọc bản `9237832`. |
| Trạng thái file contract | `x-review-status: STAGE_1_REVIEW_BACKEND_PENDING` (`api.openapi.yaml:3686`) | — | Chưa duyệt BE; ghi chú này viết trước Slice A — 2 endpoint quiz đã được BE triển khai ở commit dưới. |

- BE commit triển khai Slice A: `b8c89a4` (**chưa push**).
- Contract dùng: `engolearn-frontend/docs/handout/topic-learning/api.openapi.yaml` (không đổi trong slice này).

Hạ tầng transaction: `MongoTransactionManager` có bean, opt-in qua `engolearn.mongodb.transactions.enabled` (`src/main/java/fit/iuh/engolearn/config/MongoTransactionConfig.java:17-24`); test integration chạy `mongo:8.0.29-noble` ở replica-set mode và bật cờ (`src/test/java/fit/iuh/engolearn/config/AbstractMongoIntegrationTest.java:26-28,44`); app dev vẫn standalone (`docker-compose.yaml:4-12`) và `src/main/resources/application.yaml` không bật cờ. `design.md:84` ("Mongo standalone, không transaction manager") đã lỗi thời so với code.

Nguồn sự thật: `.artifacts/complete-topic-learning-quiz-slice/facts-be.md` (phần A contract FE, phần B hiện trạng BE, phần C ràng buộc repo).

## 2. Bảng endpoint

| # | Method | Path | Target (OpenAPI) | BE hiện tại | Ghi chú FE |
|---|---|---|---|---|---|
| 1 | GET | `/topic-attempts/{attemptId}/quiz` | `getCurrentQuiz` (`SPEC:760-763`) | **Có** (commit `b8c89a4`) — `TopicAttemptController.currentQuiz` (`:48-51`) | FE gọi khi ở quiz frontier. `410 ITEM_HIDDEN` là delta chưa sync OpenAPI. |
| 2 | POST | `/topic-attempts/{attemptId}/quizzes/{quizId}/answers` | `answerQuiz` (`SPEC:890-893`) | **Có** (commit `b8c89a4`) — `TopicAttemptController.answerQuiz` (`:53-58`) | FE gửi `{revision, optionId}` + `Idempotency-Key`. `410 ITEM_HIDDEN` là delta chưa sync OpenAPI. |
| 3 | GET | `/topic-attempts/{attemptId}` | `getAttempt` (`SPEC:516-519`) | Có — `TopicAttemptController.get` (`TopicAttemptController.java:26-29`) | Lấy attempt + revision hiện tại. |
| 4 | GET | `/topic-attempts/{attemptId}/content` | `getAttemptContent` (`SPEC:579-582`) | Có — `TopicAttemptController.content` (`:31-34`) | Nội dung pinned, chỉ đọc. |
| 5 | POST | `/topic-attempts/{attemptId}/advance` | `advanceContent` (`SPEC:642-645`) | Có — `TopicAttemptController.advance` (`:36-40`) | Không dùng cho quiz stage. |
| 6 | POST | `/topic-attempts/{attemptId}/access` | `recordAttemptAccess` (`SPEC:1402-1405`) | Có — `TopicAttemptController.access` (`:42-46`) | Ghi recency, không đổi revision. |

## 3. Request/response chi tiết (2 endpoint quiz)

### 3.1 GET `/topic-attempts/{attemptId}/quiz`

**Target:** `CurrentQuizDto` — `SPEC:3126-3148`; response envelope `{data, timestamp}` (`SPEC:436-457`).

`data` = `CurrentQuizDto`:

| Field | Kiểu | Required | Nullable | Nguồn dữ liệu thật + công thức | Ghi chú |
|---|---|---|---|---|---|
| `attempt` | `$ref AttemptDto` (`SPEC:1759-1855`) | Có | Không | `UserTopicItemAttempt` | ánh xạ qua `TopicAttemptService.describe`. |
| `quiz` | `QuizDto` \| `null` | Có | Có (`anyOf` null, `SPEC:3126-3148`) | `TopicItemVersion.quizzes` | `null` khi attempt `COMPLETED` hoặc hết quiz chưa đúng. |

`QuizDto` (polymorphic, discriminator `type`) — `SPEC:3109-3125`:
- `FILL_BLANK` → `$ref FillBlankQuizDto` (`SPEC:2929-3019`)
- `SITUATIONAL_CHOICE` → `$ref SituationalChoiceQuizDto` (`SPEC:3021-3107`)

**FillBlankQuizDto** (required `SPEC:2932-2945`, `type const` `SPEC:3001-3004`, `options` `SPEC:3011-3018`):

| Field | Kiểu | Required | Nullable | Nguồn dữ liệu thật + công thức | Ghi chú |
|---|---|---|---|---|---|
| `quizId` | string | Có | Không | Deterministic, version-scoped: `<topicItemVersionId>:quiz:<moduleOrderIndex>:<questionIndex>` (`design-review.md` Q5; `design.md:54`). `moduleOrderIndex` = `QuizModuleVo.orderIndex` (null → thứ tự trong mảng `quizzes`); `questionIndex` = thứ tự câu 0-based trong `QuizModuleVo.questions` | ổn định sau hiển thị lại |
| `title` | string | Có | Không | `QuizModuleVo.title` (`QuizModuleVo.java`) | có sẵn |
| `index` | integer, min 0 | Có | Không | Vị trí 0-based **trong toàn bộ chuỗi quiz của item, theo dạng**: dạng theo thứ tự cố định `FILL_BLANK` → `SITUATIONAL_CHOICE`; trong mỗi dạng, module theo `order_index` (null → vị trí trong mảng `quizzes`, tie-break theo vị trí gốc) rồi câu theo thứ tự mảng (`design.md:54`) | Không phải index trong module: frontier `{stage, itemIndex}` chỉ có một số cho mỗi dạng trên toàn item (`specs/topic-learn-item/spec.md:34`) |
| `totalInType` | integer, min 1 | Có | Không | Tổng số câu **cùng dạng trong toàn item** (không phải trong một module) | tính toán khi đọc |
| `situationVi` | string | Có | Có | `quiz_data.scenario_vi` (`QuizQuestionVo.quiz_data`); FILL_BLANK null hợp lệ | null khi FILL_BLANK |
| `imageUrl` | string | Có | Có | null (không trường tương ứng trong model) | null |
| `tips` | string | Có | Có | `QuizQuestionVo.tips` | có sẵn |
| `promptAudioUrl` | string | Có | Có | `QuizQuestionVo.audio_url` | có sẵn |
| `type` | string const `FILL_BLANK` | Có | Không | `QuizModuleVo.quizType` | |
| `sentenceBefore` | string | Có | Không | Tách `sentence.text_en` (`QuizSentenceVo.text_en`) tại vị trí text của option đúng, phải xuất hiện đúng 1 lần; nếu không → `CONTENT_UNAVAILABLE` | quy tắc tách chốt |
| `sentenceAfter` | string | Có | Không | Tách `sentence.text_en` tại vị trí text của option đúng, phải xuất hiện đúng 1 lần; nếu không → `CONTENT_UNAVAILABLE` | quy tắc tách chốt |
| `options` | `QuizOptionDto[]`, 4–6 | Có | Không | `QuizQuestionVo.options`; gán `optionId = "<quizId>:option:<optionPosition>"` (vị trí gốc, không phụ label, `text_en` hoặc thứ tự hiển thị) | `optionId` sinh deterministic |

**SituationalChoiceQuizDto** (required `SPEC:3024-3036`, `type const` `SPEC:3092-3095`, `options` `SPEC:3099-3106`): tương tự FillBlank, thay `sentenceBefore`/`sentenceAfter` bằng `questionVi` (string, không nullable) lấy từ `QuizQuestionVo.prompt`, options đúng 4.

`QuizOptionDto` — `SPEC:2913-2928`: `optionId` (string, required), `text` (string, required). `optionId = "<quizId>:option:<optionPosition>"` (vị trí gốc trong `QuizQuestionVo.options`, không phụ label/text/hiển thị); `text` = `text_en`. BE model chỉ có `label`/`text_en`/`is_correct` (`QuizOptionVo.java`), KHÔNG có `optionId` → BE phải sinh `optionId` deterministic.

**AttemptDto** (dùng chung) — `SPEC:1759-1855`, required `SPEC:1762-1777`: `attemptId, topicId, topicItemId, topicItemVersionId, version, mode, status, revision, position, progress, retrySessionId, startedAt, lastAccessedAt, completedAt`. `revision` `SPEC:1809-1812` (integer min 0) lấy từ `UserTopicItemAttempt.getVersion()` qua `TopicAttemptService.revision(...)`. `status` enum `IN_PROGRESS|COMPLETED` (`SPEC:1802-1808`); `mode` enum `LEARN|RETRY` (`SPEC:1795-1801`).

`PositionDto` — `SPEC:1632-1648`: `stage` (`LearningStageDto` `SPEC:1497-1506`: `CONTEXT, VOCAB, EXPRESSIONS, FILL_BLANK, SITUATIONAL_CHOICE`), `itemIndex` integer min 0.
`AttemptProgressDto` — `SPEC:1715-1758`: `completedStepCount, totalStepCount, percent, learnedVocabularyCount, learnedExpressionCount, completedQuizCount, totalQuizCount`.

Tất cả field contract của mục 3 đã có nguồn dữ liệu thật + công thức (xem bảng trên); không còn field "chưa có nguồn".

### 3.2 POST `/topic-attempts/{attemptId}/quizzes/{quizId}/answers`

**Header:** `Idempotency-Key` bắt buộc, string 1–128 (`SPEC:915-925`).

**Request body:** `AnswerQuizRequestDto` — `SPEC:3501-3517`: required `revision` (integer min 0), `optionId` (string). **Không** có `stage`, `itemIndex`, `isCorrect`, `score`, `completed` (`SPEC:894`).

**Response:** `QuizAnswerDto` — `SPEC:3209-3239`: required `quizId, isCorrect, feedback, attempt`.

| Field | Kiểu | Required | Nullable | Nguồn BE | Ghi chú |
|---|---|---|---|---|---|
| `quizId` | string | Có | Không | quiz hiện tại đã chấm | |
| `isCorrect` | boolean | Có | Không | BE chấm so `optionId` với option đúng | |
| `feedback` | `QuizFeedbackDto` \| null | Có | Có | có chỉ khi đúng; sai → `null` | không lộ đáp án/giải thích khi sai. |
| `attempt` | `$ref AttemptDto` | Có | Không | attempt sau commit | revision đã tăng. |

`QuizFeedbackDto` — `SPEC:3149-3208`: required `correctOptionId, completedSentenceEn, translationVi, explanationVi, grammarNoteVi, audioUrl`; `explanationVi` **không nullable** (`SPEC:208`), các field khác nullable. `explanationVi` lấy từ `QuizQuestionVo.explanation`; thiếu thì `CONTENT_UNAVAILABLE`. `completedSentenceEn` = `sentence.text_en` đã điền đáp án đúng vào chỗ trống. `translationVi` = `sentence.meaning_vi`. `grammarNoteVi` = null. `audioUrl` = `sentence.audio_url` (nullable).

## 4. Ma trận lỗi

Mỗi lỗi trả `ProblemDetail` media type `application/problem+json` (`SPEC:3585`), `ProblemDto` required `type, title, status, detail, code` (`SPEC:3537-3566`). Envelope thành công `{data, timestamp}` không dùng cho lỗi. Thứ tự kiểm tra bắt buộc: **replay Idempotency-Key KIỂM TRA TRƯỚC revision check** (`backend-handoff.md:113`, `SPEC:894`). Cùng key + payload giống → trả nguyên phản hồi đã ghi, không tăng revision, không thêm submission. Cùng key khác payload → 409 `IDEMPOTENCY_KEY_REUSED`. Revision cũ + key mới → 409 `STATE_CHANGED`, không chấm. Thứ tự thực tế của POST (`TopicAttemptService.answerQuiz`): `owned` 404 → key 400 → replay/reuse → `version` 404 → status 409 → `requireItemVisible` 410 → **`requireQuizFrontier` 409** → quiz 404 → current-quiz 409 → revision 409 → `requireOptionOf` 400.

### GET quiz

| Status | `code` | Khi nào | Nguồn code BE / ghi chú |
|---|---|---|---|
| 400 | `INVALID_REQUEST` | path/validation thất bại (route GET không có header/body, nên trên thực tế không xảy ra) | helper `TopicAttemptService.problem(...)` |
| 401 | `SIGN_IN_REQUIRED` | `userId` null (chưa chọn hồ sơ local) | `TopicAttemptService.userId()` |
| 404 | `CONTENT_UNAVAILABLE` | attempt không tồn tại / không sở hữu; version pin không tìm thấy; nội dung không dựng được DTO | `TopicAttemptService.owned` / `version` + `TopicQuizMapper` |
| 200 | — | attempt `COMPLETED` → `quiz:null`; hoặc hết quiz chưa đúng (câu cuối đúng, Slice B chưa completion) → `quiz:null`, status `IN_PROGRESS`, `completedAt = null`, không tạo result, reload không hiện lại câu đã đúng | Quyết định 2; **không phải 409** |
| 410 | `ITEM_HIDDEN` | item của attempt bị ẩn (`TopicItemVersion.status == HIDDEN` / release không `PUBLISHED`) | **Delta:** chưa có trong contract FE status list (`SPEC:1045-1062` chỉ 400/401/404/409/422/503) → FE phải sync OpenAPI. Code: `TopicAttemptService.requireItemVisible` |
| 409 | `STATE_CHANGED` | **chưa tới quiz frontier** — `furthestReached` null hoặc `stage` còn `CONTEXT`/`VOCAB`/`EXPRESSIONS` | `TopicAttemptService.requireQuizFrontier` — `specs/topic-quiz/spec.md:10-12`, `design.md:32`, `SPEC:764` |
| 422 | `LEVEL_TOO_LOW` / `PREDECESSOR_INCOMPLETE` | không áp quiz slice | — |
| 503 | `SERVICE_UNAVAILABLE` | persistence thực sự không sẵn sàng | `SPEC:3650-3666` |

### POST answers

| Status | `code` | Khi nào | Nguồn code BE / ghi chú |
|---|---|---|---|
| 400 | `INVALID_REQUEST` | body sai schema (`revision` thiếu/<0, `optionId` blank), Idempotency-Key thiếu/blank/>128, **`optionId` không thuộc quiz hiện tại (invalid option)** | `TopicAttemptService.answerQuiz` (key) + `requireOptionOf` (invalid option), `AnswerQuizRequest` (schema); `design.md:50` quy định 400 invalid option (không phải 404) |
| 401 | `SIGN_IN_REQUIRED` | `userId` null | `TopicAttemptService.userId()` |
| 404 | `CONTENT_UNAVAILABLE` | attempt không tồn tại/không sở hữu; `quizId` **không tồn tại** trong version đã pin; version không tìm thấy | `TopicAttemptService.owned` / `version` + `TopicQuizMapper.find` |
| 409 | `STATE_CHANGED` | `revision` cũ, frontier đã đổi, hoặc attempt đã `COMPLETED`, **hoặc `quizId` có thật nhưng không phải câu hiện tại** (đã đúng rồi / nhảy cóc). Không tính là một lần sai, không ghi submission. | `TopicAttemptService.answerQuiz` (status/current quiz/revision) + nhánh thua CAS |
| 409 | `STATE_CHANGED` | **chưa tới quiz frontier** — `furthestReached` null hoặc `stage` còn `CONTEXT`/`VOCAB`/`EXPRESSIONS`. Kiểm **sau replay, trước khi đọc quiz sequence**. **Replay cùng key/payload vẫn 200** (trả kết quả đã ghi) kể cả khi chưa tới frontier, và không ghi gì. | `TopicAttemptService.requireQuizFrontier` — `specs/topic-quiz/spec.md:10-12`, `design.md:32`, `SPEC:764`; test `answerBeforeQuizFrontierIs409WithoutAnyWrite`, `replayBeforeQuizFrontierStillReturnsTheCommittedAnswer` |
| 409 | `IDEMPOTENCY_KEY_REUSED` | cùng key nhưng payload/fingerprint khác request trước | `TopicAttemptService.receipt(...)` |
| 410 | `ITEM_HIDDEN` | item bị ẩn trước khi chấm | **Delta:** như GET quiz. Code: `TopicAttemptService.requireItemVisible`. Replay cùng `Idempotency-Key` + cùng payload **vẫn 200** kể cả khi item đã bị ẩn (replay kiểm trước visibility, không chấm, không ghi) — chủ ý, không phải lỗ hổng. |
| 422 | `LEVEL_TOO_LOW`... | không áp quiz slice | — |
| 503 | `SERVICE_UNAVAILABLE` | persistence thất bại | `SPEC:3650-3666` |

## 5. Khác biệt cần sửa (danh sách đánh số)

Mỗi mục: cái gì / vì sao / ai sửa / chặn gì.

1. **Contract FE cần sync — thêm `410 ITEM_HIDDEN`.** Contract FE cho 2 endpoint quiz chỉ khai 400/401/404/409/422/503 (`SPEC:1045-1062`), không có `410`. OpenSpec BE và spec `topic-quiz` yêu cầu `ITEM_HIDDEN (410)` (`design.md:45`, `specs/topic-quiz/spec.md:58-63`). → **FE sửa** OpenAPI/thêm status 410. Chặn: nếu BE trả 410 mà FE không xử lý, FE sẽ coi như lỗi không xác định.
2. **BE `ProblemDetail.title` bị đặt bằng `code`** (`TopicRequestExceptionHandler.java:14-15`, facts-be.md:462), ví dụ `title: "STATE_CHANGED"`; contract example muốn `title: "Learning state changed"` + `code: "STATE_CHANGED"` (`SPEC:3625-3628`). → **BE chốt** (không phải Slice A): `title` từ message dễ đọc, `code` riêng. Chặn: FE ánh xạ theo `code`, không theo `title`, nhưng contract example không khớp.
3. **Các advice khác thiếu trường `code`.** `TopicLearningExceptionHandler`, `TopicBrowseExceptionHandler`, `SecurityExceptionHandler` không set property `code` (`facts-be.md:B.5`), còn `ProblemDto.required` có `code` (`SPEC:3540-3546`). → **BE chốt** (không phải Slice A): thêm `code` cho các advice liên quan, hoặc chốt FE chấp nhận thiếu `code`. Chặn: response quiz (dùng `TopicRequestExceptionHandler` có `code`) thì OK, nhưng các lỗi 404/401 từ advice khác nếu tái dùng sẽ thiếu `code`.
4. **`501 CONTENT_UNAVAILABLE` cho `mode=RETRY`** (`TopicAttemptService.start`) không trong danh sách status contract (`SPEC:495-512`). Slice A chỉ làm LEARN. → **BE chốt:** giữ 501 (vi phạm tinh thần `backend-handoff.md:154`) hay trả 400/409. Khuyên: Slice A trả 400 `INVALID_REQUEST` nếu FE gửi mode RETRY (không xảy ra vì quiz chỉ LEARN).
5. **Replay `start` luôn trả `revision = 0`** (nhánh replay của `TopicAttemptService.start`) không phản ánh trạng thái thật (`backend-handoff.md:113`). → **BE chốt:** replay trả state hiện tại hay snapshot lúc start. Slice A không thay đổi `start`, nhưng quy tắc replay áp cho quiz answer.
6. ~~**Không có DTO quiz, không có mapper quiz, không có controller quiz.**~~ **Đã có ở `b8c89a4`:** 6 DTO `Quiz*`/`CurrentQuizResponse`/`AnswerQuizRequest`, `TopicQuizMapper`, và 2 route `TopicAttemptController.currentQuiz`/`answerQuiz` (xem §2, §3). Khi gate frontier thêm sau đó, hành vi 409 được ghi ở §4.
7. **Quiz identity (`quizId`/`optionId`) chưa có nguồn.** Model chỉ có `label`/`text_en`/`is_correct`, `QuizModuleVo.quiz_id` (`facts-be.md:B.8`). → **BE thiết toán ID deterministic** (mục 3, công thức đã chốt).
8. **Field quiz hợp đồng (`sentenceBefore/sentenceAfter/situationVi/questionVi/imageUrl/tips/promptAudioUrl/questionVi`) thiếu trực tiếp trong `QuizQuestionVo`.** → **BE chốt nguồn field + quy tắc tách câu** (mục 3 đã chốt: `sentenceBefore/After` tách `sentence.text_en` tại vị trí option đúng, xuất hiện đúng 1 lần, nếu không → `CONTENT_UNAVAILABLE`; `explanationVi` lấy `explanation`, thiếu thì `CONTENT_UNAVAILABLE`; `situationVi` lấy `quiz_data.scenario_vi`, FILL_BLANK null hợp lệ).
9. **Visibility HIDDEN đã chốt kiểm tra cho 2 endpoint quiz (quyết định 3).** → **BE thêm** hidden enforcement tại `getCurrentQuiz`/`answerQuiz` (410 `ITEM_HIDDEN`), không bật lộ ẩn/hiện cho attempt không sở hữu (luôn 404).

## 6. Ví dụ — 6.1–6.5 là ví dụ tài liệu, 6.6 là response THẬT

Mục 6.1–6.5 (và 6.7) mang nhãn `<!-- VÍ DỤ TÀI LIỆU — minh hoạ contract, KHÔNG phải response đã chạy -->`: chỉ minh hoạ contract, không có giá trị bằng chứng chạy. Mục 6.6 là response **chạy thật** trên DB clone `engolearn_quiz_verify`, port 8099, ngày 2026-10-28. Không secret, không credential, không connection string.

### 6.1 GET quiz — thành công, có câu

<!-- VÍ DỤ TÀI LIỆU — minh hoạ contract, KHÔNG phải response đã chạy -->
```http
GET /api/v1/topic-attempts/att_123/quiz
```
```json
// Response 200
{
  "data": {
    "attempt": {
      "attemptId": "att_123",
      "topicId": "topic_a2",
      "topicItemId": "item_01",
      "topicItemVersionId": "viv_01_v1",
      "version": 1,
      "mode": "LEARN",
      "status": "IN_PROGRESS",
      "revision": 5,
      "position": { "stage": "FILL_BLANK", "itemIndex": 0 },
      "progress": {
        "completedStepCount": 5, "totalStepCount": 11, "percent": 45,
        "learnedVocabularyCount": 3, "learnedExpressionCount": 4,
        "completedQuizCount": 0, "totalQuizCount": 6
      },
      "retrySessionId": null,
      "startedAt": "2026-10-27T09:00:00Z",
      "lastAccessedAt": "2026-10-27T09:10:00Z",
      "completedAt": null
    },
    "quiz": {
      "quizId": "viv_01_v1:quiz:0:0",
      "title": "Câu trống là gì?",
      "index": 0,
      "totalInType": 3,
      "situationVi": null,
      "imageUrl": null,
      "tips": "Ghi nhớ dạng thức thì quá khứ",
      "promptAudioUrl": null,
      "type": "FILL_BLANK",
      "sentenceBefore": "Could I see the ",
      "sentenceAfter": ", please?",
      "options": [
        { "optionId": "viv_01_v1:quiz:0:0:option:0", "text": "the menu" },
        { "optionId": "viv_01_v1:quiz:0:0:option:1", "text": "the bill" },
        { "optionId": "viv_01_v1:quiz:0:0:option:2", "text": "the check" },
        { "optionId": "viv_01_v1:quiz:0:0:option:3", "text": "water" }
      ]
    }
  },
  "timestamp": "2026-10-27T09:10:05Z"
}
```

### 6.2 GET quiz — attempt COMPLETED

<!-- VÍ DỤ TÀI LIỆU — minh hoạ contract, KHÔNG phải response đã chạy -->
```http
GET /api/v1/topic-attempts/att_123/quiz
```
```json
// Response 200
{
  "data": {
    "attempt": { ..., "status": "COMPLETED", "revision": 9, "completedAt": "2026-10-27T09:15:00Z", ... },
    "quiz": null
  },
  "timestamp": "2026-10-27T09:15:00Z"
}
```

### 6.3 POST answer — sai

<!-- VÍ DỤ TÀI LIỆU — minh hoạ contract, KHÔNG phải response đã chạy -->
```http
POST /api/v1/topic-attempts/att_123/quizzes/viv_01_v1:quiz:0:0/answers
Idempotency-Key: key-abc-001
```
```json
// Request
{ "revision": 5, "optionId": "viv_01_v1:quiz:0:0:option:1" }
```
```json
// Response 200
{
  "data": {
    "quizId": "viv_01_v1:quiz:0:0",
    "isCorrect": false,
    "feedback": null,
    "attempt": { ..., "revision": 6, "status": "IN_PROGRESS", "position": { "stage": "FILL_BLANK", "itemIndex": 0 }, ... }
  },
  "timestamp": "2026-10-27T09:10:10Z"
}
```

### 6.4 POST answer — đúng (commit frontier ngay)

<!-- VÍ DỤ TÀI LIỆU — minh hoạ contract, KHÔNG phải response đã chạy -->
```http
POST /api/v1/topic-attempts/att_123/quizzes/viv_01_v1:quiz:0:0/answers
Idempotency-Key: key-abc-002
```
```json
// Request
{ "revision": 6, "optionId": "viv_01_v1:quiz:0:0:option:0" }
```
```json
// Response 200
{
  "data": {
    "quizId": "viv_01_v1:quiz:0:0",
    "isCorrect": true,
    "feedback": {
      "correctOptionId": "viv_01_v1:quiz:0:0:option:0",
      "completedSentenceEn": "Could I see the menu, please?",
      "translationVi": "Tôi có thể xem menu, không?",
      "explanationVi": "Mệnh đề yêu cầu khẳng định với Could I see...",
      "grammarNoteVi": null,
      "audioUrl": null
    },
    "attempt": { ..., "revision": 7, "status": "IN_PROGRESS", "position": { "stage": "FILL_BLANK", "itemIndex": 1 }, ... }
  },
  "timestamp": "2026-10-27T09:10:12Z"
}
```

### 6.5 POST answer — replay cùng key/payload

<!-- VÍ DỤ TÀI LIỆU — minh hoạ contract, KHÔNG phải response đã chạy -->
```http
POST /api/v1/topic-attempts/att_123/quizzes/viv_01_v1:quiz:0:0/answers
Idempotency-Key: key-abc-002
```
```json
// Request (giai đoạn 6.4)
{ "revision": 6, "optionId": "viv_01_v1:quiz:0:0:option:0" }
```
```json
// Response 200 (giống phản hồi đã commit, revision 7, không thêm submission)
{
  "data": { "quizId": "...", "isCorrect": true, "feedback": {...}, "attempt": { ..., "revision": 7, ... } },
  "timestamp": "2026-10-27T09:10:12Z"
}
```

### 6.6 Response THẬT (chạy 2026-10-28, DB clone `engolearn_quiz_verify`, app port 8099)

Attempt dùng: `698a351bd05b76d98012c535` (IN_PROGRESS, 2 module / 6 câu, version pin `6a7200000000000000000021`). Dữ liệu trong clone là bản dump của `engolearn_db`, không phải mock.

**GET `/api/v1/topic-attempts/698a351bd05b76d98012c535/quiz` — 200 (nguyên văn body):**

```json
{"data":{"attempt":{"attemptId":"698a351bd05b76d98012c535","topicId":"6a7200000000000000000001","topicItemId":"6a7200000000000000000011","topicItemVersionId":"6a7200000000000000000021","version":1,"mode":"LEARN","status":"IN_PROGRESS","revision":6,"position":{"stage":"FILL_BLANK","itemIndex":0},"progress":{"completedStepCount":6,"totalStepCount":12,"percent":50,"learnedVocabularyCount":3,"learnedExpressionCount":4,"completedQuizCount":0,"totalQuizCount":6},"retrySessionId":null,"startedAt":"2026-10-27T23:14:02.037Z","lastAccessedAt":"2026-10-28T03:26:56.690Z","completedAt":null},"quiz":{"quizId":"6a7200000000000000000021:quiz:1:0","title":"Complete the sentence","index":0,"totalInType":3,"situationVi":null,"imageUrl":null,"tips":"Think about what a customer reads before ordering.","promptAudioUrl":null,"type":"FILL_BLANK","sentenceBefore":"Could I see the ","sentenceAfter":", please?","options":[{"optionId":"6a7200000000000000000021:quiz:1:0:option:0","text":"menu"},{"optionId":"6a7200000000000000000021:quiz:1:0:option:1","text":"table"},{"optionId":"6a7200000000000000000021:quiz:1:0:option:2","text":"bill"},{"optionId":"6a7200000000000000000021:quiz:1:0:option:3","text":"cake"}]}},"timestamp":"2026-10-28T03:49:14.316819700Z"}
```

**POST `/api/v1/topic-attempts/698a351bd05b76d98012c535/quizzes/6a7200000000000000000021:quiz:1:0/answers` — 200, đáp án đúng (`Idempotency-Key: verify-20261028104939-right`, option đúng lấy từ GET ở trên, nguyên văn body):**

```json
{"data":{"quizId":"6a7200000000000000000021:quiz:1:0","isCorrect":true,"feedback":{"correctOptionId":"6a7200000000000000000021:quiz:1:0:option:0","completedSentenceEn":"Could I see the menu, please?","translationVi":"Cho tôi xem thực đơn được không?","explanationVi":"Đáp án là menu (thực đơn), vì người nói muốn xem danh sách món ăn và đồ uống.","grammarNoteVi":null,"audioUrl":null},"attempt":{"attemptId":"698a351bd05b76d98012c535","topicId":"6a7200000000000000000001","topicItemId":"6a7200000000000000000011","topicItemVersionId":"6a7200000000000000000021","version":1,"mode":"LEARN","status":"IN_PROGRESS","revision":8,"position":{"stage":"FILL_BLANK","itemIndex":1},"progress":{"completedStepCount":7,"totalStepCount":12,"percent":58,"learnedVocabularyCount":3,"learnedExpressionCount":4,"completedQuizCount":1,"totalQuizCount":6},"retrySessionId":null,"startedAt":"2026-10-27T23:14:02.037Z","lastAccessedAt":"2026-10-28T03:49:40.567Z","completedAt":null}},"timestamp":"2026-10-28T03:49:40.595786300Z"}
```

**Bảng lỗi THẬT đã chạy** (mỗi dòng: request → status → body thật):

| Request | Status | Body thật |
|---|---|---|
| GET `/quiz` sau khi clone set version pin `HIDDEN` | 410 | `{"code":"ITEM_HIDDEN","detail":"The item is no longer available"}` |
| POST answers (key mới) khi item `HIDDEN` | 410 | `{"code":"ITEM_HIDDEN","detail":"The item is no longer available"}` (không thêm submission) |
| POST cùng key `...-right` nhưng `optionId=:option:2` | 409 | `{"code":"IDEMPOTENCY_KEY_REUSED","detail":"Key already used for another action"}` |
| POST key mới với `revision=7` (revision cũ) cho quiz hiện tại `...:quiz:1:1` | 409 | `{"code":"STATE_CHANGED","detail":"Reload before answering"}` |
| POST `quizId="nope:quiz:0:0"` | 404 | `{"code":"CONTENT_UNAVAILABLE","detail":"Unknown quiz"}` |
| GET `/quiz` của attempt `df386f45a330114429a336ac` (version pin hỏng) | 404 | `{"code":"CONTENT_UNAVAILABLE","detail":"FILL_BLANK option count = 2 for quiz 6ad49aa8d793f5567a3d3f85:quiz:1:0"}` |
| GET `/quiz` của attempt `38e6fedb7c981a47cb9d8fba` (version pin hỏng) | 404 | `{"code":"CONTENT_UNAVAILABLE","detail":"FILL_BLANK option count = 2 for quiz 6ad49aa8d793f5567a3d3f93:quiz:1:0"}` |
| POST `optionId="...:quiz:1:1:option:99"` (ngoài khoảng) | 400 | `{"code":"INVALID_REQUEST","detail":"optionId does not belong to the current quiz"}` |

Ghi chú: POST **thiếu** header `Idempotency-Key` trả **400** nhưng body là error body mặc định của Spring (`{"timestamp":...,"status":400,"error":"Bad Request","path":"..."}`), **không có field `code`** — xem mục 7 dòng 6 (delta đã biết, ngoài phạm vi slice).

### 6.7 POST answer — cùng key khác payload

<!-- VÍ DỤ TÀI LIỆU — minh hoạ contract, KHÔNG phải response đã chạy -->
```http
POST /api/v1/topic-attempts/att_123/quizzes/viv_01_v1:quiz:0:0/answers
Idempotency-Key: key-abc-002
```
```json
// Request (payload khác giai đoạn 6.4)
{ "revision": 6, "optionId": "viv_01_v1:quiz:0:0:option:2" }
```
```json
// Response 409
{
  "type": "https://engolearn.dev/probs/idepotency-key-reused",
  "title": "Idempotency key reused",
  "status": 409,
  "detail": "Key already used for another action",
  "code": "IDEMPOTENCY_KEY_REUSED"
}
```

## 7. Việc FE phải làm / contract cần chỉnh

1. Thêm status `410` + `code: ITEM_HIDDEN` vào OpenAPI cho 2 endpoint quiz (hiện contract không khai, facts-be.md:A.4).
2. Đồng bộ `ProblemDto` dùng cả `title` (human-readable) và `code`; ánh xạ UI theo `code`, không theo `title`.
3. Xử lý `quiz:null` khi attempt `IN_PROGRESS` nhưng hết quiz chưa đúng (edge case câu cuối, chưa COMPLETED) — FE về tổng kết item (chờ Slice B completion).
4. Giữ `Idempotency-Key` + `revision` qua reload; không tự tạo key mới mỗi lần thử lại mạng (`backend-handoff.md:112-113`).
5. Không gửi `isCorrect/score/completed` trong body (`SPEC:894`); không hiển thị đáp án/giải thích trước khi đúng.
6. 400 khi thiếu/sai `Idempotency-Key` trả body mặc định Spring **không có `code`** → FE map theo HTTP status 400; nếu cần `code` đồng nhất phải sửa handler (ngoài phạm vi slice).

## 8. Ghi chú

Tài liệu này đã được cập nhật ở bước bàn giao: response THẬT ở mục 6.6, kết quả xác minh ở mục 11, blocker dữ liệu ở mục 12. Ví dụ 6.1–6.5 và 6.7 vẫn là suy ra từ contract, chưa chạy thật; nhãn `VÍ DỤ TÀI LIỆU — minh hoạ contract, KHÔNG phải response đã chạy` được giữ trên các ví dụ đó.

## 9. Quyết định thiết kế đã chốt (5 quyết định, KHÔNG bàn lại)

1. **Cơ chế lưu: Phương án A** — `UserTopicItemAttempt` single-document CAS (`@Version` + `mutation_receipts`), KHÔNG dựng `topic_learner_runtime` ở Slice A. Hệ quả FE: BE ghi 1 document, không collection mới, FE không cần điều chỉnh do lưu trữ.
2. **Câu quiz cuối đúng mà Slice B chưa có:** `quiz:null`, status `IN_PROGRESS`, `completedAt = null`, không tạo result, reload không hiện lại câu đã đúng. Hệ quả FE: FE về tổng kết item (chờ Slice B completion), không coi là lỗi hay hết attempt.
3. **Item bị ẩn:** trả `410 ITEM_HIDDEN` (`design.md:45`). Hệ quả FE: **delta contract** — OpenAPI FE chỉ khai 400/401/404/409/422/503, FE phải thêm 410 trước khi BE trả 410, nếu không FE coi như lỗi không xác định.
4. **Data pack do agent khác nạp sau** → bỏ bước tạo script sample-data. Nếu dữ liệu thật thiếu field contract → báo blocker thật, không bịa, không hạ test. Hệ quả FE: FE không tự dựng mock data khi BE thiếu endpoint (`specs/topic-learn-item/spec.md:94-96`).
5. **Replica set 1 node:** hoãn sang trước Slice C, không thuộc Slice A. Hệ quả FE: app dev vẫn standalone, FE không ảnh hưởng.

## 10. Nợ kỹ thuật đã biết — DỜI SANG SLICE B (user chốt, không làm ở Slice A)

**Vấn đề:** item đã hiển thị và user đã đi vào, sau đó admin ẩn item đó. UI không còn item, nhưng progress của user vẫn trỏ vào nó → cần cơ chế để topic tiếp tục được.

**Luật đã có trong spec (không phải luật mới):**

| Trạng thái user ở item bị ẩn | Luật | Nguồn |
|---|---|---|
| Đang làm dở | Item ẩn bị bỏ qua ngay, kể cả item đang làm; không tự hoàn thành, không xoá history | `specs/topic-retry/spec.md:22` |
| Đang làm dở | Không giữ khoá item kế; không xuất hiện trong detail/progress/counts | `specs/topic-browse/spec.md:154-156` |
| Đã hoàn thành | Đọc result/review bị chặn; item không đóng góp tổng kết hiện hành; history vẫn còn | `specs/topic-completion/spec.md:40-42` |
| Cơ chế | Chỉ sửa ở explicit mutation, đánh `ABANDONED` kèm lý do hidden; **GET không được ghi** | `design.md:90`, `design.md:80` |

**Hai chỗ code dễ sai cùng một kiểu** (nếu tính bằng `order + 1` trên toàn bộ danh sách item):
1. **Next-item**: user xong item 5 → next = 6 (đang ẩn) → kẹt, không có 7.
2. **Predecessor check**: muốn vào 7 phải kiểm 6 hoàn thành → 6 ẩn nên không bao giờ hoàn thành → `PREDECESSOR_INCOMPLETE` → 7 cũng khoá.

Cả hai phải tính trên **danh sách đã lọc visible**. Sửa một chỗ vẫn kẹt.

**Thêm:** attempt `IN_PROGRESS` của item ẩn đang giữ unique partial index `one_running_learning_item_per_topic` (`TopicAttemptService.indexes()`), khiến `start` item kế trả `409 OTHER_ITEM_IN_PROGRESS` (nhánh `DuplicateKeyException` của `TopicAttemptService.start`). Phải giải phóng slot bằng reconciliation mutation trước khi coi là xong Slice B.

**Test bắt buộc cho Slice B:** (a) xong 5, 6 visible → next 6; (b) ẩn 6 → xong 5 → next 7, vào được, không `PREDECESSOR_INCOMPLETE`; (c) đang IN_PROGRESS ở 6, 6 bị ẩn → mutation kế tiếp đóng attempt `ABANDONED` + giải phóng index, next 7; (d) đã COMPLETED 6, 6 bị ẩn → result/review bị chặn, 6 rơi khỏi mẫu số, history còn, topic không quay về Khám phá; (e) mọi GET không ghi gì.

**Trạng thái ở Slice A:** chỉ trả `410 ITEM_HIDDEN` khi đọc/submit; **không** tự hoàn thành, **không** giải phóng slot, **không** skip. Vì Slice A chưa có chuyển item (câu cuối → `quiz:null` + `IN_PROGRESS`), giới hạn này không mới — nó chỉ trở thành vĩnh viễn nếu Slice B làm sai.

## 11. Kết quả xác minh (số liệu thật)

Môi trường: 2026-10-28, app port **8099**, DB clone **`engolearn_quiz_verify`** (dump từ `engolearn_db`, `engolearn_db` không bị ghi). Lúc chạy verify, Slice A còn ở working tree trên HEAD `e258e67`; sau đó được commit thành `b8c89a4` (nội dung code không đổi).

| Hạng mục | Kết quả thật |
|---|---|
| `./mvnw.cmd verify` | **FAIL, exit 1** — `Tests run: 246, Failures: 3, Errors: 47, Skipped: 0` |
| Chứng minh đỏ sẵn ở HEAD `e258e67` | worktree tách rời của HEAD, chạy lại đúng 9 class fail: **73 test, 3F/47E** — trùng khớp 100% ⇒ Slice A **không thêm fail nào** |
| `TopicQuizMapperTest` | **32/32** pass (31 trước review, +1 test key-set cho `FillBlankQuizDto` ở F6) |
| `TopicQuizAnswerTest` | **20/20** pass (18 trước review, +2 test gate frontier) |
| `TopicQuizReadTest` | **30/30** pass (25 trước review, +5 test gate frontier) |
| `ArchitectureTest` | **9/9** pass |
| HTTP e2e bước a–n trên clone | **PASS 16/16** — 16 dòng trong bảng `verification.md` §3.2 (a–l = 12, `m` = 3 dòng, `n` = 1), đếm bằng `Select-String "^\| [a-n] \|"` → 16 |
| Smoke gate frontier (sau review) | **PASS** — `verification.md` §6: GET `/quiz` của attempt đang ở quiz → 200, `quiz` non-null; GET + POST khi frontier `CONTEXT` → **409 `STATE_CHANGED`** (`/quizzes/nope:…` cũng 409, chứng minh gate đứng trước bước tra quiz); `version`/`receipts`/`quiz_progress` không đổi; 8080 vẫn sống |
| GET không ghi | version **6**, `mutation_receipts` **19**, `lastAccessedAt` `2026-10-28T03:26:56.690Z` — **không đổi** qua 2 lần GET liên tiếp |
| Replay cùng key/payload không ghi | version **8**, receipts **21**, submissions **2** (giống trước replay) |
| 410 `ITEM_HIDDEN` (GET + POST) không ghi | receipts **21**, submissions vẫn **2** |
| Grep body GET (a, b, d, f, m5) các token `isCorrect`, `is_correct`, `correct`, `answerData`, `answer_data`, `explanation` | **0 hit / 5 body** |
| `./mvnw.cmd -DskipTests package` | exit 0 — `target\engolearn-0.0.1-SNAPSHOT.jar`, 33,680,217 bytes (bản dùng cho e2e a–n); bản rebuild cho smoke sau review (11:23:29): 33,680,357 bytes |
| Grep body GET ở smoke §6 | GET 200 (bước a) chỉ có `quiz`/`options`, không có token `isCorrect`/`correct`/`explanation`; 2 body 409 chỉ có `detail/instance/status/title/code` |

Chi tiết từng bước HTTP (a–n), log và smoke sau review: `.artifacts/complete-topic-learning-quiz-slice/verification.md` (mục 1–3 và **mục 6**) + `evidence/`.

**Thứ tự thời gian (quan trọng khi đọc bảng):** bộ e2e a–n chạy **trước** khi gate "chưa tới quiz frontier" được thêm vào code — lúc đó `b8c89a4` chỉ có `owned` → `version` → `requireItemVisible`. Gate hiện được chứng minh bằng 7 unit test mới trong `TopicQuizReadTest`/`TopicQuizAnswerTest` (bảng trên) và **smoke HTTP §6** của `verification.md`; chưa có e2e nào chạy lại toàn bộ a–n sau khi thêm gate.

## 12. Blocker dữ liệu (thật, chưa sửa)

Các mục dưới đây là vấn đề **dữ liệu**, không phải lỗi code. BE đang **fail-closed** (trả `404 CONTENT_UNAVAILABLE` kèm detail nêu rõ field); **không nới luật** để dữ liệu sai đi qua. Hướng xử lý (sửa dữ liệu/seed, chuẩn hoá importer, hay đổi mapper) thuộc data/importer hoặc một task riêng — chưa quyết trong slice này.

1. **19/54 `topic_item_versions` vi phạm contract ⇒ `GET /quiz` trả 404 ngay câu đầu.** Hai loại vi phạm, cùng 19 version: `FB_OPTION_COUNT` **19** (FILL_BLANK options `size=2`, luật đòi 4..6) và `FB_ANSWER_OCCURRENCES` **19** (text đáp án không xuất hiện trong `sentence.text_en`, luật đòi đúng 1 lần). Trong 19 version lỗi: **16 PUBLISHED, 3 HIDDEN**.
   Ví dụ: version `6ad49aa8d793f5567a3d3f85` → `{"code":"CONTENT_UNAVAILABLE","detail":"FILL_BLANK option count = 2 for quiz 6ad49aa8d793f5567a3d3f85:quiz:1:0"}`.
2. **2/3 attempt `IN_PROGRESS` (LEARNING) thật của learner pin version hỏng ⇒ 404 khi mở lên:** `df386f45a330114429a336ac` (version `6ad49aa8d793f5567a3d3f85`) và `38e6fedb7c981a47cb9d8fba` (version `6ad49aa8d793f5567a3d3f93`). Đã xác nhận bằng HTTP thật trên clone.
3. **90/105 câu SITUATIONAL thiếu `quiz_data.scenario_vi`** — field `scenario_vi` nằm ở **cấp question**, còn mapper chỉ đọc trong `quiz_data` (theo handoff mục 3.1) ⇒ response trả `situationVi: null`. Số liệu: **90/105** câu bị; **30 version** chỉ có mỗi vấn đề này (không 404, chỉ mất ngữ cảnh tiếng Việt); **15/105** câu đúng chỗ.
