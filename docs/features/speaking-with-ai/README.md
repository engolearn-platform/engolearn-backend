# Speaking với AI — ánh xạ nghiệp vụ đến BE

Trang này đối chiếu BE trên nhánh `dev` với đặc tả nghiệp vụ. Bằng chứng hiện tại dựa trên mã nguồn và các test hiện có. Các kiểm tra trực tiếp với provider, micro, trình duyệt và tải vẫn cần xác minh.

## Nguồn và phạm vi

- Đặc tả nghiệp vụ chuẩn: [module và BR-38.*](../../../../docs/engolearn-business-doc/docs/usecase_spec/speaking-with-AI/00-module.md), [Feature 1 rules](../../../../docs/engolearn-business-doc/docs/usecase_spec/speaking-with-AI/f01-speaking-session/rules.md), [Feature 2 rules](../../../../docs/engolearn-business-doc/docs/usecase_spec/speaking-with-AI/f02-speaking-history/rules.md), [Feature 3 rules](../../../../docs/engolearn-business-doc/docs/usecase_spec/speaking-with-AI/f03-speaking-configuration/rules.md), cùng các UC riêng trong từng thư mục.
- Thiết kế kỹ thuật và quyết định trạng thái: [tài liệu kỹ thuật Speaking](../../technical/speaking-ai/README.md).
- Mã BE dẫn chiếu dưới đây là bằng chứng triển khai, không thay thế đặc tả nghiệp vụ.

## Trạng thái tổng quan

| Phạm vi | Trạng thái BE | Giới hạn chính |
| --- | --- | --- |
| BR-38.1–38.11, 38.13 | Một phần | Có luồng Speaking, snapshot nội dung, xác thực và quyền sở hữu. Chưa có bằng chứng BE triển khai hết mọi hành vi bảo mật, quyền và lưu giữ theo yêu cầu. Feature 3 chưa có workflow quản trị/publish. |
| BR-38.12 | Chưa xác minh | Chưa có bằng chứng kiểm thử tải 10–20 phiên đồng thời hoặc p95 theo yêu cầu. |
| SPK-UC-001–004 (F01) | Một phần | Tạo phiên, hội thoại realtime, đánh giá, kết thúc và phục hồi đã có; một số ngữ nghĩa kết thúc, bền vững và đo thời gian còn lệch/thiếu kiểm chứng. |
| SPK-UC-005–007 (F02) | Một phần | API đọc lịch sử/chi tiết, phát audio có hạn và xóa mềm đã có; SLA, retention thực tế và tính bất biến trước tác vụ đến muộn chưa xác minh đầy đủ. |
| SPK-UC-008–010 (F03) | Chưa triển khai | Có model cấu hình và dữ liệu dev; chưa có màn hình/quy trình Content Manager, lưu bản nháp, phân loại MINOR/MAJOR hoặc chặn publish topic. |

Luồng đánh giá mặc định dùng `SpeakingConversationComposer`, Gemini với Prompt V2, bộ kiểm tra bản nháp và `ConversationManager`. Khi tắt manager, BE dùng `GeminiConversationEvaluator`. Xem [luồng đánh giá](../../technical/speaking-ai/runtime-flows.md#1-realtime-speaking-turn) và [cấu hình runtime](../../technical/speaking-ai/module-contract.md#runtime-selection-and-rollback).

## Luật chung BR-38.*

| Luật | Ánh xạ và đánh giá |
| --- | --- |
| BR-38.1–38.4 | Speaking lưu phiên riêng và chỉ đọc Topic qua `SpeakingItemLookup`; không cập nhật tiến độ, mở khóa hay gọi Làm lại. **Đã có ranh giới BE.** UI hiện vào từ màn hình kết quả item; xem [đối chiếu FE](../../../../engolearn-frontend/docs/features/speaking-with-ai/user-flows.md) cho khoảng trống so với tab ở mọi chế độ. |
| BR-38.5–38.6 | REST lấy learner từ `CurrentUserProvider`; các route Speaking được bảo vệ ở `SecurityConfig`; service kiểm tra ownership trước thao tác session. Chưa có API authoring/publish và chưa xác minh đầy đủ ma trận Content Manager. Trạng thái: **một phần**. |
| BR-38.7 | `SpeakingItemLookup.findOpenableItem` dùng kiểm tra khả dụng item; `SpeakingSessionService.config/create` gọi contract này. Trạng thái: **đã nối điều kiện mở**, cần kiểm thử Topic theo các mode đầy đủ. |
| BR-38.8 | Session chụp `SpeakingContentSnapshot` lúc tạo; history/detail đọc snapshot của phiên. Cấu hình mới không được áp ngược vào snapshot cũ. Trạng thái: **đã có cơ chế snapshot**. |
| BR-38.9 | Prompt yêu cầu phản hồi tiếng Anh và phần dẫn/giải thích tiếng Việt. Kết quả phụ thuộc đánh giá mô hình; không có kiểm định chất lượng ngôn ngữ runtime. **Một phần**. |
| BR-38.10 | Các ngưỡng vận hành được đặt trong BE/FE, không do Content Manager sửa theo item. Số lượt hợp lệ tối thiểu thuộc cấu hình nội dung từng item, khác với giới hạn tối đa 30 lượt của phiên. |
| BR-38.11 | Turn, assessment và audio metadata là các bản ghi riêng. Transcript nằm trong turn và được chép vào assessment khi có đánh giá hợp lệ. Audio bytes lưu qua MediaStorage. Chưa xác nhận việc xóa, ẩn và lưu giữ đáp ứng toàn bộ quyền và thời hạn nghiệp vụ. **Một phần**. |
| BR-38.12 | **Chưa xác minh** tải 10–20 phiên và **chưa thấy** cơ chế giới hạn tải toàn module để từ chối tạo phiên với lý do quá tải. Pool xử lý lượt không phải bộ kiểm soát số phiên đang hoạt động. |
| BR-38.13 | Filter chặn REST/WS Speaking khi có profile `prod`; ngoài `prod` cờ BE mặc định bật, không giới hạn riêng cho profile `dev`. BE dùng cookie và ID từ tài khoản, không nhận định danh demo do client tự khai. Đây là **khác biệt so với mô tả thử nghiệm**; các fixture vẫn chỉ chạy với `dev`. |

## Ánh xạ theo use case

| UC | Triển khai BE | Trạng thái / khoảng trống |
| --- | --- | --- |
| [SPK-UC-001 — Mở phiên](../../../../docs/engolearn-business-doc/docs/usecase_spec/speaking-with-AI/f01-speaking-session/SPK-UC-001_mo_phien_luyen_noi.md) | `SpeakingController.create/current/replace`; `SpeakingSessionService.create/config`; `SpeakingItemLookup`; `SpeakingContentSnapshot`. | **Một phần.** Có mở và chụp nội dung. Quota hiện là tổng thời lượng sử dụng theo ngày Asia/Ho_Chi_Minh (60 phút) cùng giới hạn session 20 phút/30 lượt, không phải quota/subscription tích hợp đã chứng minh. Cần đối chiếu quyền tạo/replace, điều kiện quota và thực tế Subscription. |
| [SPK-UC-002 — Hội thoại theo lượt][uc002] | `SpeakingSocketHandler`, `SpeakingRealtimeService`, `SpeakingOpeningService`, `SpeakingConversationComposer` và các adapter. | **Một phần.** Có audio streaming, mở lời AI/learner, chữ tạm/chốt và đánh giá từng lượt. Với bằng chứng mục tiêu V2, BE kiểm tra ID đã ghim, chưa đạt và quote có nội dung. Mô hình chịu trách nhiệm đối chiếu quote mục tiêu với lời nói. Kiểm tra lời nói vẫn áp dụng riêng cho tài nguyên và góp ý sửa lỗi. |
| [SPK-UC-003 — Hoàn thành/kết thúc][uc003] | `SpeakingSessionService.end/finish/finalizeClosing`, `ConversationStateView`, `ConversationManager`. | **Một phần.** Với snapshot `REQUIRED_GOALS_V2`, đủ mục tiêu Bắt buộc và số lượt hợp lệ cho phép kết thúc `COMPLETED`; đủ mọi mục tiêu và số lượt cho phép tự kết thúc. Trạng thái cuối và lý do được ghi riêng. Cửa sổ xử lý lượt chờ chưa đáp ứng BR-F1.23. |
| [SPK-UC-004 — Gián đoạn/sự cố][uc004] | `SpeakingRealtimeService` quản lý kết nối; `SpeakingSessionService.resume/interruptIfDisconnected/recoverAfterRestart`. | **Một phần.** Mất socket dẫn tới gián đoạn sau thời gian chờ, không giả lập End; resume có thể chuyển quyền, còn restart đi vào luồng kết thúc `SERVICE_RESTART`. Chưa kiểm chứng thu hồi quyền giữa phiên, mất mạng thực tế hoặc đa instance. |
| [SPK-UC-005 — Xem lịch sử][uc005] | `SpeakingController.history`; `SpeakingSessionService.history` lọc theo người học, tùy chọn item, bỏ dữ liệu xóa mềm. | **Đã có.** Đọc cấp tài khoản không kiểm tra lại item có còn hiển thị; không phụ thuộc mic hay hạn mức. Chưa đo p95 ≤ 2 giây; khi API/DB không khả dụng thì không có đường đọc riêng. |
| [SPK-UC-006 — Xem chi tiết][uc006] | `SpeakingSessionService.get/playback/openingPlayback` đọc snapshot của phiên và audio thuộc phiên. | **Đã có.** URL ký có hiệu lực 60 giây. Bản ghi người học và audio mở lời AI có cửa sổ phát 30 ngày. Đây là giới hạn truy cập, dữ liệu vẫn được lưu. Chưa đo p95 chi tiết ≤ 2 giây hay bắt đầu phát ≤ 1 giây. |
| [SPK-UC-007 — Xóa Speaking][uc007] | `SpeakingSessionService.delete/deleteAll` kết thúc phiên đang mở, chờ trạng thái cuối rồi ghi `deletedAt`; đọc chi tiết và phát lại kiểm tra ownership/deletion. | **Đã có xóa mềm.** Chưa kiểm chứng đầy đủ thao tác đồng thời, kết quả đến muộn và hết hạn của URL đã cấp trước khi xóa. Không thay đổi dữ liệu tiến độ Topic. |
| [SPK-UC-008 — Soạn cấu hình nháp][uc008] | `SpeakingItemConfig`, `SpeakingGoal`, repository và dev seed lưu cấu hình dùng để mở phiên. | **Chưa có luồng Content Manager.** Đọc cấu hình đã publish không thay thế việc soạn bản nháp, kiểm tra quyền hay áp dụng giá trị mặc định ở bước publish. |
| [SPK-UC-009 — Kiểm tra publish][uc009] | `SpeakingSessionService.publishedConfig` kiểm tra cấu hình của version khi đọc cấu hình hoặc mở phiên. | **Chưa có.** Topic publish chưa gọi bộ kiểm tra này để chặn phát hành cả topic và trả danh sách item/trường lỗi theo BR-F3.6–3.7. BR-F3.8 đã bỏ. |
| [SPK-UC-010 — Cập nhật sau publish][uc010] | `SpeakingContentSnapshot` giữ nội dung lúc tạo phiên, độc lập với cấu hình hiện tại. | **Có snapshot; chưa có workflow cập nhật.** Chưa có tác vụ phân loại MINOR/MAJOR cho Speaking, phát hành version và thông báo người học. |

[uc002]: ../../../../docs/engolearn-business-doc/docs/usecase_spec/speaking-with-AI/f01-speaking-session/SPK-UC-002_thuc_hien_luot_noi.md
[uc003]: ../../../../docs/engolearn-business-doc/docs/usecase_spec/speaking-with-AI/f01-speaking-session/SPK-UC-003_hoan_thanh_ket_thuc_phien.md
[uc004]: ../../../../docs/engolearn-business-doc/docs/usecase_spec/speaking-with-AI/f01-speaking-session/SPK-UC-004_gian_doan_ket_noi_su_co.md
[uc005]: ../../../../docs/engolearn-business-doc/docs/usecase_spec/speaking-with-AI/f02-speaking-history/SPK-UC-005_xem_lich_su_phien.md
[uc006]: ../../../../docs/engolearn-business-doc/docs/usecase_spec/speaking-with-AI/f02-speaking-history/SPK-UC-006_xem_chi_tiet_phien.md
[uc007]: ../../../../docs/engolearn-business-doc/docs/usecase_spec/speaking-with-AI/f02-speaking-history/SPK-UC-007_xoa_du_lieu_speaking.md
[uc008]: ../../../../docs/engolearn-business-doc/docs/usecase_spec/speaking-with-AI/f03-speaking-configuration/SPK-UC-008_soan_cau_hinh_speaking.md
[uc009]: ../../../../docs/engolearn-business-doc/docs/usecase_spec/speaking-with-AI/f03-speaking-configuration/SPK-UC-009_kiem_tra_dieu_kien_publish.md
[uc010]: ../../../../docs/engolearn-business-doc/docs/usecase_spec/speaking-with-AI/f03-speaking-configuration/SPK-UC-010_cap_nhat_cau_hinh_sau_publish.md

## Đối chiếu luật F01–F03

### F01 — SPK-UC-001–004

| Nhóm luật | Bằng chứng và giới hạn |
| --- | --- |
| BR-F1.1–1.9 | Tạo phiên là lệnh riêng sau khi FE xin mic. BE kiểm tra Topic, snapshot, quota và một phiên mở theo người học. 60 phút/ngày Asia/Ho_Chi_Minh tính từ các đoạn ghi âm, chờ đánh giá tối đa 10 giây/lượt và thời gian xử lý TTS; chưa đo thời gian người học thực nghe và chưa tích hợp Quota/Subscription. Chưa đo p95 mở phiên hay xác minh ma trận trình duyệt. |
| BR-F1.10–1.21 | Luồng tuần tự theo controller, STT tạm/chốt và đánh giá theo lượt. BE áp tuổi 20 phút, tối đa 30 lượt chốt và giới hạn thu PCM. Frame vượt ngưỡng 120 giây làm lượt lỗi. Kết quả STT rỗng đi qua `TURN_EMPTY` trước khi ghi turn. Các trường hợp xác nhận chốt với nội dung rỗng hoặc chưa lưu bền theo D-12/D-15 chưa có trong runtime này. `INVALID` và lỗi đánh giá tách biệt. Ba lỗi liên tiếp có thể kết thúc. Chưa xác minh SLA, nhận diện ≥ 80% từ khóa hay cảnh báo mạng yếu theo ngưỡng nghiệp vụ. |
| BR-F1.22–1.27, 1.36–1.40 | `finish` đợi tối đa 10 giây khi còn `PENDING`. Tầng session có thể nhận kết quả manager trong cửa sổ `LEARNER_ENDED` hoặc `TURN_LIMIT`, nhưng cổng realtime manager dừng khi bắt đầu đóng. Lượt thứ 30 có thể bị bỏ đánh giá và kết quả đang xử lý có thể bị bỏ khi người học bấm kết thúc. Lượt còn `PENDING` ở hạn chót chưa chuyển thành `UNRESOLVED` theo BR-F1.23/D-13. BE chốt theo bằng chứng đã ghi. Chưa có lưu payload bền để phục hồi hay ghi nguyên tử toàn bộ lượt/assessment/progress. Snapshot cũ giữ chính sách tỷ lệ và số lượt đã ghim. |
| BR-F1.28–1.35 | FE thử reconnect. BE giữ phiên và epoch trong thời gian chờ, rồi có thể chuyển `INTERRUPTED` sau 10 giây nếu chưa có socket mới. REST resume chuyển quyền qua epoch mới. Đóng hoặc thay socket có thể chuyển lượt manager đang chờ thành `FAILED` mà không tăng bộ đếm lỗi đánh giá. Âm thanh chưa commit phải nói lại. Startup đưa phiên mở vào luồng kết thúc, chưa tiếp tục đánh giá đang chờ. Chưa chứng minh đầy đủ logout, thu hồi quyền giữa phiên, mất mạng trên trình duyệt hay phục hồi đa instance. |

BR-F1.41 được thể hiện một phần trong prompt và manager quyết định dialogue. Prompt hướng đến xử lý ý hiện tại trước, một nhánh tiếp nối và chào ngắn khi learner muốn dừng. Đây là hướng dẫn cho LLM, không phải đảm bảo chất lượng phản hồi hay điều khiển lifecycle từ riêng nội dung model.

### F02 — SPK-UC-005–007

API đọc/xóa và playback hiện hữu; audio có hạn phát lại, signed playback URL ngắn hạn. BR-F2.2, F2.6 về latency chưa có đo đạc xác nhận. BR-F2.4/F2.9 yêu cầu giữ dữ liệu hệ thống sau khi hết hạn/soft delete trong khi người học mất quyền truy cập; mã có expiry và soft-delete path nhưng cần xác nhận bằng persistence/integration tests rằng mọi đường lấy dữ liệu đều tôn trọng điều đó.

### F03 — SPK-UC-008–010

`SpeakingItemConfig` và `publishedConfig` bao phủ phần hình dạng BR-F3.1–3.5 khi chạy: 3–5 mục tiêu, ít nhất một Bắt buộc, 3–5 lượt tối thiểu, tình huống, hai vai, bên mở lời và tài nguyên từ item. Phiên mới dùng `REQUIRED_GOALS_V2`; trường tỷ lệ cũ chỉ phục vụ tương thích snapshot. Việc trả `CONFIG_MISSING`/`CONFIG_INVALID` chặn Speaking cho item đó, chưa đáp ứng chặn publish cấp topic (BR-F3.6–3.7). Snapshot hỗ trợ BR-F3.11; luồng cập nhật BR-F3.9–3.10 còn thiếu.

## Thiết kế đã chấp nhận và phạm vi kiểm chứng

Hợp đồng dev/demo đã chấp nhận D-12/D-13 và D-15. Các mục này quy định cách tính lượt chốt, xử lý lượt chưa giải quyết và biểu diễn nội dung chưa lưu bền. Trạng thái chấp nhận thiết kế và các điều kiện rollout được ghi trong [v0.8.2](../../../../docs/system-design/speaking-demo-contract-v0.8.2-addendum.md) và [v0.8.3](../../../../docs/system-design/speaking-demo-contract-v0.8.3-addendum.md).

Để lần theo mã và test, xem [module map](../../technical/speaking-ai/module-map.md) và [runtime flows](../../technical/speaking-ai/runtime-flows.md). Kết quả kiểm thử lịch sử của đợt cleanup trên `feat/speaking-ai-baseline` được lưu trong Git tại `a84a8f5:docs/technical/speaking-ai/README.md`.
