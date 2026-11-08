# Placement: kiểm tra trước khi push — 09/11/2026

## Kết luận hiện tại

Placement đã qua các kiểm tra nêu bên dưới sau khi sửa hai lỗi mới phát hiện.
Chưa thể khẳng định toàn BE/FE không còn lỗi tiềm ẩn. Toàn BE vẫn thất bại ở
55 trường hợp đã có từ trước; không ghi nhận trường hợp lỗi mới trong bộ kiểm
thử. Người dùng đã đồng ý push hai nhánh riêng kèm báo cáo lỗi nền sau khi xem kết
quả audit, với điều kiện không push/merge main. Phần thay đổi được bàn giao để
leader review, không được hiểu là toàn bộ dự án đã xanh hoặc main đã được merge.
application.yaml local có trước được loại khỏi commit.

## Nhánh và mô phỏng ghép main

| Repo | Nhánh riêng | HEAD trước audit | origin/main |
|---|---|---|---|
| BE | merge/placement-guest-user-main | 7944d7f | 0f41690 |
| FE | fix/placement-guest-navbar | 083f75a | 59078cc |

Cả hai nhánh chứa main đã fetch ở thời điểm kiểm tra: ahead 9 / behind 0.
Dùng index/cây Git tạm bao gồm các thay đổi local được chọn, loại trừ chỉnh sửa
application.yaml có trước, rồi chạy merge-tree: cả hai không xung đột. Không
checkout main, không cập nhật nhánh main, không tạo merge commit trên nhánh,
không push. Mô phỏng không thay index làm việc; có tạo các object tạm không
gắn ref. Cần fetch và kiểm tra lại ngay trước lần merge thực tế nếu main thay đổi.

Không xung đột Git không bảo đảm không có xung đột nghiệp vụ. Leader cần review
cặp BE/FE cùng nhau, đặc biệt các điểm chung sau:

- User sở hữu cập nhật level; Placement gọi contract mới để chỉ tăng/giữ level,
  giữ C1/C2, không save đè hồ sơ. Level thay đổi có chủ đích ảnh hưởng nội dung
  Chủ đề đang đọc hồ sơ. Không sửa code nghiệp vụ Chủ đề.
- Navbar/sidebar/route chung FE được tái sử dụng. Review trên đĩa không thấy mất
  nhóm route của main; nội dung chưa lưu trong editor không nằm trong audit/Git.
- Mongo replica set/transaction và profile dev,placement-local là điều kiện của
  luồng Placement thật. Không đưa application.yaml local, .env, credentials,
  target, node_modules, DB volume hay log test lên Git.
- Data migration chỉ đổi vị trí file/công cụ/test sang tools/placement/data.
  Import inputs V1/V2, câu hỏi, snapshot và 96 WAV giữ nguyên byte. Không cần
  import lại Mongo; đề PUBLISHED vẫn được ưu tiên hơn DRAFT.

## Hai lỗi đã sửa trước bàn giao

1. BE transactional submit bỏ mất revision đã kiểm tra: tab khác có thể xóa
   hết đáp án giữa lúc kiểm tra và nộp. Nay revision thay đổi trả về vòng lặp
   kiểm tra của caller, không nộp bài trắng/cập nhật level sai.
   Test HTTP/Mongo thật đã thấy RED trước sửa và GREEN sau sửa.
2. FE flush đáp án thất bại sau hết giờ làm chặn nhận trạng thái đã kết thúc.
   Nay đọc GET thật; chỉ tiếp tục POST submit idempotent khi BE đã terminal.
   Nếu BE vẫn IN_PROGRESS thì giữ lỗi lưu, không nộp bỏ qua đáp án chưa lưu.
   Kiểm tra Chrome tách biệt dùng actual React hook, real proxy/BE/Mongo:
   trước sửa server AUTO_SUBMITTED nhưng FE result null; sau sửa nhận result.

## Kết quả đã quan sát

| Kiểm tra | Kết quả |
|---|---|
| BE mvnw.cmd verify sau sửa | 691 test; 7 failure, 48 error, 29 skip — chưa xanh toàn bộ |
| So danh sách lỗi với lượt trước/baseline được ghi nhận | Đúng 55 identity cũ; 0 identity lỗi mới |
| Placement trong full verify | 142 test: 141 pass, 1 Windows symlink skip |
| Level real HTTP/Mongo + Architecture targeted | 5 + 9 pass |
| Real FE→BE→Mongo User/Guest/claim/level | 79 kiểm tra pass |
| FE production build | Pass; còn cảnh báo chunk lớn |
| FE lint | 0 error, 72 warning hiện có |
| Auth return | 4/4 pass |
| UI deadline regression | auto, empty, active pass; không mock API |
| Package check | Pass; converter/snapshot/importer parsing/96 WAV hashes |
| Mô phỏng ghép main cả hai repo | Không xung đột; main/ref HEAD không đổi |

Các lỗi baseline có trong nhóm test context/auth/topic/subscription; chưa sửa
nghiệp vụ của các thành viên khác để làm xanh. Chi tiết log đầy đủ nằm ở workspace
C: (prepush-be-verify-final.log, prepush-fe-*.log, prepush-live-results.json,
prepush-deadline-*.log, prepush-audit-results.json); không commit log hoặc credential.

## Dữ liệu và giới hạn

Các kiểm tra live tạo tài khoản/bài test riêng trong DEV. Công cụ deadline chỉ
rút ngắn deadline của Guest test mới tạo (lọc ID, user_id null, trạng thái active
và started_at của phiên kiểm tra), không thay đề hoặc bài cá nhân. Giữ lại record
test để đối chiếu. Chrome profile/HTML/listener tạm được dọn; phiên browser cá
nhân không đăng xuất/ghi đè. Các kiểm thử Mongo tích hợp dùng DB test riêng.

Chưa kiểm thử mọi module của nhóm qua UI, chưa chờ 15 phút wall-clock với bài
cá nhân, không cam kết production hoặc main tương lai sẽ an toàn tuyệt đối.
Đề xuất bàn giao nhánh riêng kèm báo cáo này để leader review; việc merge main
là bước riêng, không nằm trong thao tác đã được thực hiện.
