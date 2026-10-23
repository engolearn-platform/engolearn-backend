## Why

`TopicLearningServiceImpl` trộn lẫn business logic (precondition, transaction, version resolution) với boilerplate mapping VO → DTO (khoảng 170 dòng). Service nên tập trung vào nghiệp vụ, mapping nên được tách ra lớp riêng để dễ đọc, dễ test và dễ duy trì.

## What Changes

- Thêm layer `mapper` ngang cấp `controller`, `service`, `repository`, `dto` trong package `fit.iuh.engolearn.topic`.
- Tạo class `TopicItemMapper` (thuần Java, static methods) chứa toàn bộ phương thức mapping từ `TopicLearningServiceImpl`.
- Cập nhật `TopicLearningServiceImpl` để delegate mapping sang `TopicItemMapper`.
- Cập nhật `docs/architecture/package-structure.md`: thêm layer `mapper` vào cây package và bảng dependency policy.
- Cập nhật `ArchitectureTest`: thêm `"mapper"` vào `FEATURE_LAYERS`.
- Viết unit test cho `TopicItemMapper` (plain JUnit, không Spring context).

## Capabilities

### New Capabilities

- (none)

### Modified Capabilities

- (none)

## Impact

- Code mới: package `fit.iuh.engolearn.topic.mapper` (1 class mapper).
- Code đã sửa: `TopicLearningServiceImpl` (xoá block mapping, delegate sang mapper), `package-structure.md`, `ArchitectureTest`.
- Không đổi API, không đổi business logic, không thêm Maven dependency.
- `skip_specs: true` vì không thay đổi spec-level behavior (pure refactor).
