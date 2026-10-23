## 1. Bổ sung tài liệu validation policy

- [x] 1.1 Thêm mục validation policy vào `docs/architecture/package-structure.md`: dùng Spring Bean Validation ở controller để validate shape, service chỉ enforce business rule. Verify: tài liệu có mục mới.

## 2. Thêm validation annotation vào DTOs và @Valid vào controller (làm chung)

- [x] 2.1 Thêm `@NotBlank` vào `topicItemId` của `GetTopicItemContentRequest`, `GetTopicItemPositionRequest`, `SaveTopicItemPositionRequest`; `@NotBlank` vào `topicId` của `GetTopicProgressRequest`. Verify: DTO compile.
- [x] 2.2 Thêm `@NotNull` vào `stage` và `@Min(0)` vào `itemIndex` của `SaveTopicItemPositionRequest` và `UpdateTopicItemPositionRequest`. Verify: DTO compile.
- [x] 2.3 Thêm `@Valid` vào các tham số request của 4 endpoint trong `TopicLearningController`. Verify: controller compile.

## 3. Xoá guard boilerplate từ service, giữ business rules

- [x] 3.1 Xoá `requireUserId`, `requireTopicItemId`, `requireText`, `requireStage`, `requireItemIndex` khỏi `TopicLearningServiceImpl`. Verify: service compile.
  - Note: `requireTopicItemId`, `requireText`, `requireStage`, `requireItemIndex` đã xoá. `requireUserId` được giữ lại vì nó enforce precondition "đã đăng nhập" (authorization rule), không phải shape validation; xoá nó phá vỡ test `aGuestCannotReadTheContent`.
- [x] 3.2 Cập nhật các method gọi guard để không còn gọi chúng (dùng trực tiếp giá trị hoặc exceptions nghiệp vụ). Verify: service compile, business rules chưa đổi.

## 4. Viết controller validation test

- [x] 4.1 Thêm test cases vào `TopicLearningControllerTest` cho các trường hợp shape invalid: `itemId` trống, `stage` null, `itemIndex` âm, v.v. Verify: test pass, trả 400 ProblemDetail.

## 5. Verify toàn diện

- [x] 5.1 Chạy `mvnw verify`. Verify: tất cả test pass (bao gồm ArchitectureTest), không có vi phạm arquitectura.
