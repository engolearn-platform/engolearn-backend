## Context

`TopicLearningServiceImpl` hiện có 775 dòng, trong đó khoảng 170 dòng là mapping boilerplate (`toContentResponse`, `toContextResponse`, `toDialogueTurnResponse`, `toVocabularyResponse`, `toWordExampleResponse`, `toQuickCheckResponse`, `toExpressionBlockResponse`, `toExpressionPurposeResponse`, `toExpressionExampleResponse` + helpers `firstText`, `textList`). Việc trộn mapping vào service khiến service khó đọc và khó test riêng.

Nguồn đơn sûr: `package-structure.md` định nghĩa layer của feature. Hiện không có layer `mapper`, cần thêm.

## Goals / Non-Goals

**Goals:**
- Tách mapping VO → DTO ra lớp mapper riêng, ngang cấp các layer khác.
- Service chỉ chứa business logic (precondition, transaction, version resolution).
- Mapper class thuần Java, static methods, không Spring bean, không MapStruct.
- Test mapper bằng plain JUnit, không cần Spring context.

**Non-Goals:**
- Thêm MapStruct dependency (cần phê duyệt theo AGENTS.md).
- Thay đổi business logic của service.
- Thay đổi API shape.

## Quyết định

### D-1: Vị trí package mapper

Quyết định: `fit.iuh.engolearn.topic.mapper`, ngang cấp `controller`, `service`, `repository`, `dto`.

Lý do: tuân thủ package-structure.md (feature-first, layer-second). Mapper là kiến thức của feature topic, không phải cross-cutting nên không đưa vào `shared`.

Alternatives: `shared.mapper` — bị loại vì `shared` không được biết về feature.

### D-2: Kiểu class mapper

Quyết định: class thuần Java, static methods, không `@Component`, không `@Service`.

Lý do: mapping là pure transformation, không có state, không cần Spring context. Dễ test bằng plain JUnit.

Alternatives: `@Component` + inject vào service — bị loại vì thêm độ phức tạp không cần thiết.

### D-3: Không dùng MapStruct

Quyết định: viết mapper thuần Java, không thêm MapStruct.

Lý do: AGENTS.md cấm thêm dependency mà không phê duyệt; `toQuickCheckResponse` có logic đọc Map tùy chỉnh, MapStruct làm rất khó; chỉ có 9 phương thức mapping, mapper thuần Java đủ sạch.

Alternatives: MapStruct — bị loại.

## Rủi ro / Trade-offs

[Risk] Refactor mapping có thể bỏ sót field. → Giả giảm: viết test mapper cover tất cả phương thức, chạy `mvnw verify` sau khi xong.
[Risk] Service delegate gọi mapper sai cách. → Giả giảm: test service hiện có (30 tests) vẫn pass sau refactor.

## Migration Plan

Không cần migration data. Refactor chỉ thay đổi mã Java, không ảnh hưởng database.

## Câu hỏi còn mở

- (không có)
