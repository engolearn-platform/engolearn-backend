## Why

`TopicLearningController` hiện không có input shape validation ở tầng controller. Các guard như null/blank check đang nằm trong `TopicLearningServiceImpl`, trộn lẫn với business rule. Cần tách ra: controller dùng Spring Bean Validation để validate shape (null/blank/range), service chỉ enforce business rule (level, item trước, item đang dở, read-only completed item).

## What Changes

- Thêm annotation Bean Validation (`@NotBlank`, `@NotNull`, `@Min`) vào request DTOs: `GetTopicItemContentRequest`, `GetTopicItemPositionRequest`, `GetTopicProgressRequest`, `SaveTopicItemPositionRequest`, `UpdateTopicItemPositionRequest`.
- Thêm `@Valid` vào các tham số request của `TopicLearningController` endpoints.
- Xoá guard boilerplate (`requireUserId`, `requireTopicItemId`, `requireText`, `requireStage`, `requireItemIndex`) từ `TopicLearningServiceImpl`, giữ business rules.
- Thêm test cho controller validation (400 ProblemDetail khi shape invalid).
- Bổ sung tài liệu: quy định dùng Spring Bean Validation ở controller, service chỉ enforce business rule.

## Capabilities

### New Capabilities

- (none)

### Modified Capabilities

- (none)

## Impact

- Code mới: validation annotations trên DTOs, `@Valid` trên controller, test validation.
- Code đã sửa: `TopicLearningController`, `TopicLearningServiceImpl`, request DTOs, `docs/architecture/package-structure.md`.
- Không đổi API shape, không đổi business logic, không thêm Maven dependency (`spring-boot-starter-validation` đã có trong `pom.xml`).
- `skip_specs: true` vì không thay đổi spec-level behavior (refactor validation layer).
