# Grammar User v4 — BE integration 22/11/2026

Source chỉ sửa `grammar/dto/GrammarLearnerResponses.java` và `grammar/mapper/GrammarPracticeMapper.java`. Feedback của câu **đã trả lời** có thêm `type`/`prompt` từ pinned revision; không đổi collection hoặc schema. Không trả câu tương lai hay acceptedAnswers. Không sửa Placement, User hoặc module Topic.

Compile đạt. Kiểm tra RED → GREEN qua FE proxy/BE/MongoDB thật đạt; giữ chấm server, 5 câu, ngưỡng 3/5, bestScore max, completed vĩnh viễn, reload, thứ tự, idempotency, quyền USER/Admin và hết hạn. Bộ kiểm tra dùng nội dung thật được sao từ DB local vào DB kiểm thử tạm, không dùng bộ câu hỏi fixture/mock. DB kiểm thử/tiến trình phụ được dọn sau kiểm tra; không đổi `.env`/application.yaml trong lượt này.

Chủ dự án đã cho phép publish riêng mẫu A1 `0fe8ce08-ddec-4e2f-bff5-74883627be5f`, 3 Lesson; learner API local xác nhận thấy mẫu. 31 Topic khác được đối chiếu không đổi và vẫn DRAFT. Seed trong tools vẫn DRAFT để mỗi môi trường duyệt riêng; không import lại để ép DB local về trạng thái seed.

Đọc báo cáo FE `docs/features/grammar/02-user-v4-result.md` tại repo frontend để xem toàn bộ file/màn, cách chạy và giới hạn. Không commit, không push hoặc merge main.
