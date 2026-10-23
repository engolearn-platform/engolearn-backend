## 1. Cập nhật tài liệu kiến trúc

- [x] 1.1 Thêm layer `mapper` vào cây package trong `docs/architecture/package-structure.md`. Verify: tài liệu phản ánh package mới.
- [x] 1.2 Thêm dòng `| mapper | ... |` vào bảng dependency policy trong `package-structure.md`. Verify: bảng có dòng mapper.

## 2. Cập nhật ArchitectureTest

- [x] 2.1 Thêm `"mapper"` vào `FEATURE_LAYERS` trong `ArchitectureTest.java`. Verify: test compile và pass.

## 3. Tạo mapper class

- [x] 3.1 Tạo `TopicItemMapper` tại `fit.iuh.engolearn.topic.mapper.TopicItemMapper`. Verify: class compile, chứa 9 phương thức mapping từ service.

## 4. Refactor service delegate sang mapper

- [x] 4.1 Xoá block `// --- mapping` trong `TopicLearningServiceImpl`. Verify: service compile, không còn code mapping.
- [x] 4.2 Thay bằng delegate gọi `TopicItemMapper.toContentResponse(...)`. Verify: service compile, method signature không đổi.

## 5. Viết test cho mapper

- [x] 5.1 Tạo `TopicItemMapperTest` (plain JUnit). Verify: test pass, cover null context, null quickCheck, expressions rỗng, nhiều biến thể key trong quickCheck.

## 6. Verify toàn diện

- [x] 6.1 Chạy `mvnw verify`. Verify: tất cả test pass (bao gồm ArchitectureTest), không có vi phạm arquitectura.
