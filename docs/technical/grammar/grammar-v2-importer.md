# Grammar — import và chuyển đổi

Bộ hiện hành và các lệnh dry-run nằm ở [README dữ liệu](../../../tools/mongo-json-importer/samples/grammar/README.md). Định dạng chung `{metadata, collections}`, UUID String, snake_case và Extended JSON. Không thêm field/schema hoặc sinh tiến độ người dùng để phục vụ seed.

## Namespace Grammar mới

Xác nhận Topic/Lesson/counter/progress/session/revision/unlock Grammar trống, đúng DB đích, sao lưu và dừng biên soạn. Không yêu cầu các module khác trống. Dry-run gói nội dung và counter bootstrap trước; chỉ Apply sau khi xem báo cáo. Generic importer không có transaction chung xuyên tất cả collection; lỗi giữa chừng phải đối chiếu trạng thái rồi mới tiếp tục. Không dùng replace/skip để che dữ liệu khác nhau.

Seed chuẩn gồm43 Topic/106 Lesson/468 khối/530 câu, toàn bộ Draft/ready=false, progress rỗng. Counter bootstrap snapshot=44 có khoảng trống mã lịch sử; không đặt bằng số Topic43 hoặc hạ counter đang dùng. Nội dung nhập trước, counter sau. Không nạp cùng các mẫu A1 legacy/V2 cũ do mã có thể trùng nhưng ID khác.

## DB có dữ liệu legacy

Mặc định chỉ đọc:

```powershell
.\tools\mongo-json-importer\grammar-v2-migrate.ps1
```

Sau khi kiểm tra report, sao lưu, dừng ghi và được phép chuyển:

```powershell
.\tools\mongo-json-importer\grammar-v2-migrate.ps1 -TopicId '<ID từ report>' -Apply
```

Apply yêu cầu MIGRATE, kiểm tra lại source/hash/version/counter/order/child conflict, chuyển từng Topic trong transaction. Giữ ID/code, child version, draft/published, first_published_at, revisions/progress/session/audit; tăng parent edit token. Không tự sửa counter thấp hoặc nội dung xung đột. Topic đã chuyển thành công trước lỗi ở Topic sau vẫn giữ; chạy preflight mới để tiếp tục. ALREADY_V2 xác nhận cấu trúc, không xác nhận nội dung vẫn giống snapshot cũ.

Không migrate/import tự động khi khởi động app. Các mẫu A1 và ValidateGrammarSeed còn được giữ để kiểm tra tương thích legacy/V2; chúng không phải bundle nhập mặc định và không thay xác minh toàn bộ học liệu hiện hành.

Sau nạp: Admin mở `/admin/grammar`, kiểm tra từng bài → Sẵn sàng → xuất bản; học viên `/grammar` chỉ nhìn thấy Published theo quyền cấp. Kết quả biên soạn/xuất bản trên một DB local không được áp đặt lên DB của thành viên khác.
