# Grammar Model V2 Implementation Plan

> CẬP NHẬT: chủ dự án đã duyệt tiếp bước tích hợp V2. Implementation và kiểm chứng hiện hành xem docs/technical/grammar-v2-runtime.md và grammar-v2-importer.md; DB đang dùng chưa được chuyển đổi.

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task. Do not dispatch work to other agents unless separately requested or required by an applicable skill.

**Goal:** Tách persistence Topic/Lesson Grammar theo model V2 được duyệt, bảo toàn API và dữ liệu hiện có, rồi bàn giao mẫu học liệu A1.

**Architecture:** Repository ghép các document thành aggregate cho service/mapper đang chạy. Schema marker chọn nguồn legacy hoặc V2 theo từng Topic. Migration explicit, version-checked và transactional; không tự chạy startup.

**Tech Stack:** Java 21, Spring Boot, Spring Data MongoDB, MongoDB replica-set transactions, React/TypeScript frontend API proxy; không thêm dependency.

**Spec:** `2026-11-20-grammar-model-v2-design.md` cùng thư mục bản kế hoạch này.

## Global Constraints

- Làm trên nhánh hiện tại ở ổ D; không push/merge main và không tự tạo worktree mới.
- Chỉ Grammar; không thay API/DTO để bắt FE phải dựng lại giao diện.
- Không ghi MongoDB hoặc áp dụng migration/import trong lượt chuẩn bị.
- Không dùng mocked API/fixture để xác nhận tích hợp; kiểm tra tĩnh phải báo đúng giới hạn.
- Giữ UUID/String và mọi liên kết hiện có; không reset dữ liệu module khác.
- Nháp cho phép chưa đủ bài/khối/câu, tối đa 10/7/5. Khi Sẵn sàng: 1–7 khối và đúng 5 câu/Lesson; khi xuất bản Topic: 1–10 Lesson hoạt động đều đạt kiểm tra. Tỷ lệ loại câu là quyết định biên soạn.

## Review Focus

- Topic legacy vẫn đọc/ghi được trong khi Topic V2 tồn tại; không tự đổi nguồn dữ liệu.
- Admin lưu cùng lúc và Learner chốt điểm trong lúc lưu trữ: bảo toàn version/fence và transaction.
- Topic có Lesson archived hoặc vừa đổi thứ tự: thống kê/phân trang/checklist dùng đúng tập hoạt động.
- Migration chạy lại hoặc trùng ID nhưng nội dung khác: kiểm tra không biến thành replace.
- DB dev đã có mã Topic/mẫu A1: không lùi counter, cấp lại mã hay xóa tiến độ để nạp mẫu.

### Task 1: Chốt model và tài liệu chuyển đổi

**Files:** tạo bản đề xuất `D:/TrenLop/WWWJava/Nhom/data/data_1.grammar-v2.proposed.json`; thiết kế và kế hoạch trong `engolearn-backend/docs/superpowers/specs/` và `plans/` hiện có.

**Interfaces:** model mô tả snake_case và enum persistence; API GrammarAdminDtos/GrammarLearnerResponses giữ nguyên.

- [x] Đối chiếu rules/DTO/model/repository trước khi viết đề xuất.
- [x] Sinh bản model mới, parse JSON và so sánh giữ nguyên mọi định nghĩa ngoài Grammar (13 định nghĩa giữ nguyên).
- [x] Rà soát model so với VO thực tế, phân biệt file mô tả với JSON import; bốn bộ enum đã đối chiếu. Fresh builder review đã xử lý các điểm về nháp chưa đủ nội dung, CAS/delete child, migration nguồn và timestamp.
- [ ] Bàn giao các lựa chọn chi tiết để duyệt trước Task 2.

### Task 2: Persistence tách collection và tương thích legacy

**Files:** các root document V2 mới trong `src/main/java/fit/iuh/engolearn/grammar/model/`; mapper trong `grammar/mapper/`; `MongoGrammarAdminRepository.java`, `MongoGrammarLearnerRepository.java`; helper storage trong `grammar/repository/`. Không đổi HTTP controller hoặc shared.

**Interfaces:** giữ `GrammarAdminRepository.findTopic/search/counts/insertTopic/saveTopic/deleteTopic` và `GrammarLearnerRepository.findPublishedTopics/findPublishedTopic/fencePublishedLesson`. Mapper trả mô hình aggregate hiện tại.

- [ ] Viết kiểm tra `separateTopicDoesNotEmbedLessonContent`, `legacyTopicDoesNotAutoMigrate`, `mixedStorageSearchFiltersAndCounts` với nội dung A1 thật được duyệt; quan sát FAIL trước implementation.
- [ ] Thêm root document V2 tuân BaseDocument/@Field, mapping và nguồn đọc theo schema_version; repository ghi trong transaction hiện có.
- [ ] Đổi join/projection cho danh sách, thống kê và learner; không phân trang bằng cách load toàn DB vào Java.
- [ ] Chứng minh rollback/version/fence với `concurrentLessonWritesCannotLoseChanges`, `archiveDuringPracticePreservesPinnedRevision`, `activeLessonLimitAndOrderRemainAtomic` trên DB tạm được cho phép.
- [ ] Kiểm tra `projectedAggregateCannotDeleteChildren`, `deleteTopicRemovesOnlyItsOwnChildren`, `deleteVersusEditHasOneConsistentOutcome`; ghi child bắt buộc chạm parent trong cùng transaction và kiểm tra lại caller token sau retry.
- [ ] Cập nhật tài liệu Grammar và rà diff để xác nhận không đổi HTTP contract.

### Task 3: Công cụ migration mặc định chỉ đọc

**Files:** công cụ và README Grammar trong tool importer hiện có; không thêm migrator startup vào business service.

**Interfaces:** đầu vào DB dev + danh sách Topic; mặc định trả preflight gồm ID, source version/hash, số Lesson, xung đột, trạng thái đã chuyển; Apply là switch riêng.

- [ ] Kiểm tra `preflightPerformsNoWrites`, `migrationRefusesChangedSourceAndConflictingLesson`, `migrationPreservesIdsAndVersions`, `secondApplyDoesNotDuplicateLessons` trước phần tương ứng.
- [ ] Implement kiểm tra code/order/counter/references, transaction CAS khi Apply và fail closed khi khác dữ liệu.
- [ ] Kiểm tra `unsupportedStandaloneObjectIdSourceIsRejected`, `migrationTimestampDoesNotInventLessonHistory`, `editedMigratedTopicIsNeverReplacedByRerun`; nguồn chỉ là embedded UUID hiện tại, checksum loại metadata root mới.
- [ ] Bàn giao lệnh preflight và Apply riêng; lượt này không gọi Apply.

### Task 4: Bộ mẫu A1 và cách nạp

**Files:** bundle chọn đích rõ ràng trong `tools/mongo-json-importer/samples/grammar/`; README và ghi chú nguồn cùng vị trí để tránh default all-samples tự nạp vào DB đang dùng.

**Interfaces:** `{metadata,collections}` của generic importer. ID ổn định, snake_case đúng persistence V2; không seed runtime user/progress/session/unlock/audit.

- [x] Soạn bản nội dung đọc được gồm ba bài và lời giải, 15 khối, 15 câu; đã tự rà MCQ và biến thể FIB. File `docs/technical/grammar-a1-content-review.md`; chưa phải bundle import, chưa chạy checklist Java/DB.
- [ ] Kiểm tra real bundle: field/enum/count/ID/code/references/normalization; chạy mapper và GrammarChecklistValidator thật, không gọi mạng hoặc ghi DB.
- [ ] Giữ DRAFT/ready=false; không ghi số liệu mockup hay tự cấp trạng thái PUBLISHED.
- [ ] Viết hướng dẫn dùng DB Grammar rỗng, xử lý counter khi DB đã có dữ liệu và kiểm tra Admin → xuất bản → learner sau khi được phép nạp.

### Task 5: Nghiệm thu và báo cáo

- [ ] Run BE `./mvnw.cmd verify` phù hợp môi trường, phân biệt lỗi tồn tại ngoài Grammar với lỗi thay đổi này.
- [ ] Run FE `npm run build`, `npm run lint` nếu source FE thay đổi; kiểm tra thực tế qua proxy và BE với DB tạm khi được cho phép.
- [ ] Ghi lệnh đã chạy, kết quả và phần chưa chạy. Không gọi “hoàn thiện tích hợp” khi chưa thử DB thật.
- [ ] Liệt kê chính xác file đổi; không commit/push thay đổi sẵn có của người dùng theo mặc định.
