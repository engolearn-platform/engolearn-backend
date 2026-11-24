# Grammar V2 — storage, API và quy tắc

## Storage

Topic `schema_version=2` nằm trong `grammar_topics`; bài học tách ở `grammar_lessons`, giữ ID UUID String và `topic_id`. Tiến độ ở `grammar_lesson_progress`. Topic legacy không có marker hoặc marker=1 vẫn đọc/ghi lesson nhúng để tương thích; không tự migrate khi startup hoặc khi học viên đọc.

Admin tạo Topic mới theo V2. Ghi child/parent bằng version CAS trong transaction Mongo; không lưu projection thiếu child trở lại DB. Phiên luyện tập ghim revision đã xuất bản; nội dung sửa sau đó không được thay câu/đáp án giữa lượt. Giữ revision/progress/session/unlock/counter/lock/audit khi cập nhật hoặc migration.

## Quản trị

API gốc `/api/v1/grammar/admin/topics`, `ApiResponse` cho thành công, `ProblemDetail` cho lỗi. CONTENT_MANAGER hoặc ADMIN qua service Spring `@PreAuthorize` của nhóm.

| Thao tác | Endpoint dưới API gốc |
| --- | --- |
| Danh sách / tạo | GET / POST |
| Chi tiết / sửa | GET / PUT `/{id}` |
| Kiểm tra / xuất bản / lưu trữ / khôi phục | GET `/{id}/checklist`; POST `/{id}/publish`, `/archive`, `/restore` |
| Thêm bài / đọc, preview, sửa bài | POST `/{id}/lessons`; GET / PUT `/{id}/lessons/{lessonId}`; GET đường dẫn bài + `/preview` |
| Kiểm tra / Sẵn sàng / áp dụng / bỏ nháp bài | GET đường dẫn bài + `/checklist`; POST + `/ready`, `/apply`, `/discard-draft` |

Topic xuất bản cần 1–10 bài hợp lệ; bài Sẵn sàng cần checklist 14 mục, 1–7 khối và đúng 5 câu. Bản nháp được lưu khi chưa đủ nội dung. Không áp tỷ lệ loại câu cố định. Giữ edit token/version và phản hồi xung đột; không ghi đè bản của biên tập viên khác. Danh sách hỗ trợ tìm literal không dấu/không phân biệt hoa thường, không tự sửa chính tả hay hiểu fuzzy search.

## Học viên

API gốc `/api/v1/grammar`: GET `/roadmap`, `/topics/{topicId}`, `/topics/{topicId}/lessons/{lessonId}`; POST đường dẫn bài + `/seen`, `/practice`; GET `/practice/{sessionId}` và POST + `/answers`. Cookie, quyền USER kế thừa và ownership đều phải hợp lệ.

- Chỉ dùng nội dung Published. Topic trong cấp đã mở có thể chọn tự do; bài trong cùng Topic học tuần tự.
- `/seen` ghi nhận phiên bản nội dung đã hiển thị, không chứng minh người học đã đọc hiểu. FE nhắc đọc trước khi luyện tập, không chặn bằng thời gian hay cuộn trang.
- Bài đạt từ 3/5; chủ đề hoàn thành khi đạt tất cả bài Published. Phiên đã nộp cho phép xem feedback của câu đã nộp, gồm type/prompt/explanation; không lộ đáp án câu chưa làm.
- Mở cấp tiếp theo khi hoàn thành ít nhất `ceil(70 * P / 100)` chủ đề Published đủ điều kiện ở cấp trước (P>0). Không dùng phần trăm UI làm tròn để cấp quyền. Cấp đã lưu mở được giữ; không hạ unlock cũ.
- Grammar đọc level hồ sơ qua User contract; không tự nâng level hồ sơ hoặc bắt buộc kỳ đánh giá định kỳ mới. Xem [tích hợp level](grammar-placement-level-integration.md).

Học liệu và cách nạp xem [importer](grammar-v2-importer.md). Các DTO/controller/service trong `src/main/java/fit/iuh/engolearn/grammar` là căn cứ chi tiết cho payload/validation; FE giữ `/grammar` và `/admin/grammar` với layout của nhóm.
