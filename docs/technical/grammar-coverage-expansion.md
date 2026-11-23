# Grammar — bổ sung các khoảng trống học liệu A1–B2

Ngày 23/11/2026. Chủ dự án yêu cầu kiểm tra bài/chủ đề thiếu và bổ sung theo nguồn uy tín; không thêm khi đã đủ mục tiêu. Phạm vi chỉ học liệu Grammar, không thay UI/model/API hay module khác.

## Thiết kế đã chọn theo yêu cầu
Đối chiếu toàn bộ 65 bài hiện có với mục tiêu/khối/câu hỏi và các mục Grammar A1-A2/B1-B2 của British Council. Nguồn CEFR/RLD chỉ định hướng năng lực; phân cấp riêng A1/A2/B1/B2 và lựa chọn mục tiêu là quyết định biên soạn, không khẳng định có một danh mục CEFR bắt buộc duy nhất.
Không thay toàn bộ 32 chủ đề bằng bộ ZIP. Thêm Lesson vào nhóm phù hợp; chỉ tạo Topic khi mục tiêu không thuộc nhóm đang có. Dùng tên ngữ pháp Anh/Việt riêng đúng schema. Một bài 1–7 khối, đúng 5 câu theo validator; giữ quy ước 3MCQ/2FIB khi biên soạn. Không thêm bài nghe hoặc loại câu hỏi mới. Bài biến đổi câu dùng lựa chọn/điền khuyết có kiểm soát; chưa có chấm câu tự do hay kéo thả.

## Khoảng trống cần biên soạn (chốt sau kiểm tra trùng)
- A1: tần suất; biến đổi câu hiện tại đơn theo ngữ cảnh; have got; the/zero article; giới từ thời gian; who/why/how; tân ngữ/sở hữu/chỉ định.
- A2: đếm được/không đếm được và đơn vị; few/little khác a few/a little; đại từ bất định; so sánh bằng; for/since; mệnh đề thời gian tương lai; tính từ/trạng từ; giới từ chuyển động.
- B1: be/get used to và would; khả năng quá khứ; verb+preposition và cấu trúc phrasal verbs; câu hỏi đuôi/gián tiếp lịch sự; mức độ so sánh/so-such; từ hạn định; động từ trạng thái; đại từ phản thân.
- B2: wish/if only; mức độ tính từ; as/like; quá khứ hoàn thành tiếp diễn; trạng từ liên kết/dấu câu; mức chắc chắn tương lai.
Đây là bổ sung mục tiêu mới, không phải tăng số câu vì một Topic đang có hai bài. Với mục đã có ở cấp thấp hơn, chỉ thêm khi có chức năng hoặc độ sâu khác được ghi rõ.

## Tính toàn vẹn
Sao lưu Grammar hiện tại, kiểm tra nguồn trước ghi. Nội dung cũ và ID/progress/session/revision giữ nguyên; thêm cuối danh sách Lesson hiện có để không đổi thứ tự học cũ. Topic mới có thể chèn vị trí phù hợp về sư phạm nhưng giữ thứ tự tương đối giữa các Topic cũ. Root UUID/code do API Admin cấp; không tự viết counter/ID vào DB. Content block/question ID ổn định theo key biên soạn. Không reset/import replace database.
Mọi bổ sung giữ Nháp, không tự Ready/Publish; mẫu A1 3 bài PUBLISHED không bị thay revision. Bài mới ở Topic published vẫn DRAFT. Luồng public chỉ nhận dữ liệu đã duyệt, do đó bài mới được xem Admin trước.

## Seed dùng chung
Cập nhật cùng file samples/grammar/grammar-cefr-a1-b2.v2.importer.json theo {metadata,collections}, 3 core collections, progress=[]; không tách database/bộ seed hoạt động riêng. Giữ core data cũ (chỉ thay parent lesson_sequence/order cần thiết và tên/mô tả nhóm nếu phạm vi mở rộng). Counter bootstrap chỉ dành namespace Grammar trống, lấy đúng sequence mới, không ghi đè counter local.

## Kiểm tra
Đọc nguồn thật, tự viết ví dụ/câu hỏi; lưu link và lỗi truy cập trong docs. Rà một đáp án đúng/ý nghĩa, kiến thức đã dạy, biến thể chấm và khối lỗi thường gặp. Chạy DTO/checklist/normalizer production trên nội dung thật trước API. Đọc lại Admin API+MongoDB sau ghi; bảo toàn nội dung cũ/tiến độ/phiên/revision, thứ tự, code và trạng thái. Xuất seed và đối chiếu ID/link/enum/count; không dùng mock/fixture làm bằng chứng tích hợp. Lượt này không sửa Java/TS nên không chạy toàn test app nếu không có lý do mới. Báo rõ scope/giới hạn và đường dẫn Admin để duyệt.
