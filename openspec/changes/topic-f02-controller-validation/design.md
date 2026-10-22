## Context

`TopicLearningController` hiện không validate input shape. Service `TopicLearningServiceImpl` có các guard method (`requireUserId`, `requireTopicItemId`, `requireText`, `requireStage`, `requireItemIndex`) dùng để kiểm tra null/blank/range — đây là shape validation, không phải business rule. Việc trộn shape validation vào service khiến service khó đọc và khó tách biệt nghiệp vụ.

## Goals / Non-Goals

**Goals:**
- Controller là single gate cho input shape validation, dùng Spring Bean Validation (`@Valid`, `@NotBlank`, `@NotNull`, `@Min`).
- Service chỉ enforce business rule (level ordering, predecessor completion, read-only completed item, …).
- Không thêm Maven dependency (`spring-boot-starter-validation` đã có trong `pom.xml`).
- Validation lỗi trả 400 `ProblemDetail` (RFC 7807) qua `@RestControllerAdvice` hiện có.

**Non-Goals:**
- Thay đổi business logic.
- Thay đổi API shape.
- Thêm validation vào service (ngoài business rules).

## Quyết định

### D-1: Vị trí validation

Quyết định: Shape validation ở controller qua `@Valid` + annotations trên DTO. Business rule validation ở service.

Lý do: tách biệt rõ ràng, controller nhận input đầu tiên, service chỉ xử lý nghiệp vụ. Dễ test controller validation bằng MockMvc, dễ test service business rule bằng unit test.

### D-2: Cách thêm `@Valid` cho `@PathVariable`

Quyết định: Thêm `@Valid` trực tiếp trước `@PathVariable` (Spring 4.x hỗ trợ). Ví dụ: `@Valid @PathVariable("itemId") String itemId`.

Lý do: Spring 4.x (Spring Boot 4.1.1) hỗ trợ `@Valid` trên `@PathVariable`, `@RequestParam`, `@RequestBody`.

### D-3: Các annotation dùng

Quyết định: `@NotBlank` cho string không được null/blank, `@NotNull` cho enum/Integer không được null, `@Min(0)` cho itemIndex không được âm.

Lý do: đơn giản, đủ cho shape validation, không cần custom validator.

## Rủi ro / Trade-offs

[Risk] `@Valid` trên `@PathVariable` có thể không được hỗ trợ ở một số phiên bản Spring cũ. → Giả giảm: Spring Boot 4.1.1 hỗ trợ, test sẽ xác nhận.
[Risk] Xoá guard khỏi service có thể để reactivation nếu validation controller bị bỏ qua (trực tiếp gọi service). → Giả giảm: service vẫn giữ business rule validation (không phải shape), các caller thông qua controller đều bị validate.

## Migration Plan

Không cần migration data. Refactor chỉ thay đổi mã Java, không ảnh hưởng database.

## Câu hỏi còn mở

- (không có)
