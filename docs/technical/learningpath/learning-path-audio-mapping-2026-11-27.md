# Bảng ánh xạ audio Learning Path — chờ duyệt

> Bảng dưới đây giữ lại kết quả đối chiếu trước di chuyển. Chủ dự án đã duyệt và
> đợt di chuyển 30 passage ACTIVE đã hoàn tất; xem
> [kết quả và Media ID đã áp dụng](learning-path-media-migration-2026-11-27.md).

Đối chiếu ngày 27/11/2026 (Asia/Saigon), DB local `engolearn_db`. Chỉ đọc DB, file và JSON nguồn; chưa upload thêm, chưa sửa DB/JSON nguồn. Bảng đã được tự rà soát kỹ thuật; chưa phải phê duyệt di chuyển dữ liệu.

## Phạm vi đề xuất duyệt

| Nhóm | Số lượng | Đề xuất |
| --- | --- | --- |
| Passage Listening ACTIVE | 30 | Ánh xạ 30 file; dùng lại 1 Media, upload mới 29 file sau khi duyệt |
| Passage Listening RETIRED | 5 | Để riêng, chưa upload và chưa đổi tham chiếu |
| Passage DRAFT đã có Media | 1 | Giữ nguyên, không upload lại, chưa xuất bản |
| Snapshot Listening cũ | 300 | Để riêng: 240 URL giả, 60 URL local |

## 30 passage đang sử dụng

Mọi passage dưới đây có level B1, file tồn tại và ffprobe đọc được MP3. Cột thời lượng là đo file thực tế, làm tròn 2 chữ số. Tất cả khớp title/transcript/skill/level với bundle nguồn và khớp title/transcript với parts tương ứng. Không suy ra chất lượng nghe từ phép đo này.

| Passage ID | Revision DB | Tiêu đề | File trong public/learning-path-audio/ | Giây | Câu ACTIVE | Media đề xuất |
| --- | --- | --- | --- | --- | --- | --- |
| LP-PASS-L-B1-V2-001 | 1 | Updates to the Number 42 Bus Route | b1-v2/listening-001.mp3 | 46.58 | 5 | Dùng lại M01 |
| LP-PASS-L-B1-V2-002 | 1 | Evening Museum Hours for Workers | b1-v2/listening-002.mp3 | 36.83 | 5 | Chưa có; upload sau duyệt |
| LP-PASS-L-B1-V2-003 | 1 | Riverside Clean-up Changes | b1-v2/listening-003.mp3 | 50.66 | 5 | Chưa có; upload sau duyệt |
| LP-PASS-L-B1-V2-004 | 1 | A New Club Meeting Place | b1-v2/listening-004.mp3 | 48.41 | 5 | Chưa có; upload sau duyệt |
| LP-PASS-L-B1-V2-005 | 1 | A Greener Way to Shop | b1-v2/listening-005.mp3 | 47.66 | 5 | Chưa có; upload sau duyệt |
| LP-PASS-L-B1-V2-006 | 1 | New Clinic Appointment System | b1-v2/listening-006.mp3 | 36.46 | 5 | Chưa có; upload sau duyệt |
| LP-PASS-L-B1-V2-007 | 1 | Library Workshop Move | b1-v2/listening-007.mp3 | 43.95 | 5 | Chưa có; upload sau duyệt |
| LP-PASS-L-B1-V2-008 | 1 | A Safer Ride for the Weekend | b1-v2/listening-008.mp3 | 48.84 | 5 | Chưa có; upload sau duyệt |
| LP-PASS-L-B1-V2-009 | 1 | Town Festival Schedule Change | b1-v2/listening-009.mp3 | 49.42 | 5 | Chưa có; upload sau duyệt |
| LP-PASS-L-B1-V2-010 | 1 | A Bountiful Day at Oak Street Garden | b1-v2/listening-010.mp3 | 48.63 | 5 | Chưa có; upload sau duyệt |
| LP-PASS-L-B1-V2-011 | 1 | A New Chapter for Lunch at The Green Bistro | b1-v2/listening-011.mp3 | 48.54 | 5 | Chưa có; upload sau duyệt |
| LP-PASS-L-B1-V2-012 | 2 | Evening Career Workshop | b1-v2/listening-012.mp3 | 46.28 | 5 | Chưa có; upload sau duyệt |
| LP-PASS-L-B1-V2-013 | 1 | Quiet Mornings at the Sports Center | b1-v2/listening-013.mp3 | 54.72 | 5 | Chưa có; upload sau duyệt |
| LP-PASS-L-B1-V2-014 | 1 | Platform Shift at Central Station | b1-v2/listening-014.mp3 | 52.62 | 5 | Chưa có; upload sau duyệt |
| LP-PASS-L-B1-V2-015 | 1 | The Community Repair Cafe | b1-v2/listening-015.mp3 | 56.25 | 5 | Chưa có; upload sau duyệt |
| LP-PASS-L-B1-V2-016 | 1 | Organizing Our Book Drive | b1-v2/listening-016.mp3 | 52.56 | 5 | Chưa có; upload sau duyệt |
| LP-PASS-L-B1-V2-017 | 1 | A Greener Living Space | b1-v2/listening-017.mp3 | 61.26 | 5 | Chưa có; upload sau duyệt |
| LP-PASS-L-B1-V2-018 | 1 | Welcome to the Language Exchange | b1-v2/listening-018.mp3 | 51.54 | 5 | Chưa có; upload sau duyệt |
| LP-PASS-L-B1-V2-019 | 1 | The Oakwood Park Trail Update | b1-v2/listening-019.mp3 | 52.48 | 5 | Chưa có; upload sau duyệt |
| LP-PASS-L-B1-V2-020 | 1 | A Bargain at the Grand Theater | b1-v2/listening-020.mp3 | 50.44 | 5 | Chưa có; upload sau duyệt |
| LP-PASS-L-B1-V2-021 | 1 | A Kitchen Challenge | b1-v2/listening-021.mp3 | 54.41 | 5 | Chưa có; upload sau duyệt |
| LP-PASS-L-B1-V2-022 | 1 | The New Pick-up Point | b1-v2/listening-022.mp3 | 49.12 | 5 | Chưa có; upload sau duyệt |
| LP-PASS-L-B1-V2-023 | 1 | Focusing on Our Gallery Opening | b1-v2/listening-023.mp3 | 51.21 | 5 | Chưa có; upload sau duyệt |
| LP-PASS-L-B1-V2-024 | 1 | Convenience at Corner Market | b1-v2/listening-024.mp3 | 46.60 | 5 | Chưa có; upload sau duyệt |
| LP-PASS-L-B1-V2-025 | 1 | The Final Push | b1-v2/listening-025.mp3 | 53.58 | 5 | Chưa có; upload sau duyệt |
| LP-PASS-L-B1-V2-026 | 1 | Protecting Our Shoreline | b1-v2/listening-026.mp3 | 54.04 | 5 | Chưa có; upload sau duyệt |
| LP-PASS-L-B1-V2-027 | 1 | Updates to Summer Swim Lessons | b1-v2/listening-027.mp3 | 50.42 | 5 | Chưa có; upload sau duyệt |
| LP-PASS-L-B1-V2-028 | 1 | Greening Oak Street | b1-v2/listening-028.mp3 | 47.29 | 5 | Chưa có; upload sau duyệt |
| LP-PASS-L-B1-V2-029 | 1 | A Change of Venue for the Orchestra | b1-v2/listening-029.mp3 | 46.96 | 5 | Chưa có; upload sau duyệt |
| LP-PASS-L-B1-V2-030 | 1 | Town Hall: The Riverside Project | b1-v2/listening-030.mp3 | 52.13 | 5 | Chưa có; upload sau duyệt |

Lưu ý khi tự rà soát: passage `LP-PASS-L-B1-V2-012` đang ở revision 2; các passage ACTIVE còn lại ở revision 1. Phải dùng revision thực tế khi lập điều kiện cập nhật, không mặc định cả bộ là revision 1.

### M01: audio đã upload — không upload trùng

- Media ID: `6d649d45-1f23-4e1b-8a70-5a26802a9327`; purpose `LEARNING_PATH_PASSAGE_AUDIO`, policy `PROTECTED`, MIME `audio/mpeg`.
- Đang được bản nháp `LP-PASS-118fbaf8-8105-4aa2-8aaf-876279ff4500` tham chiếu; ACTIVE `LP-PASS-L-B1-V2-001` hiện vẫn dùng URL local.
- File hiện tại khớp SHA-256 biên nhận upload: `72580eeaaacb3c8fbeebb66594c3cbc3b250f793c1088c779a5d413035222785`. Title/transcript/skill/level của DRAFT và ACTIVE khớp nhau. Đề xuất dùng chung Media, không tạo bản upload thứ hai.
- Đây là đối chiếu biên nhận và dữ liệu local; khả năng phát thật của Media đã kiểm chứng ở bước 5, không gọi/upload Cloudinary lại trong lần thống kê này.

## 5 passage đã ngừng sử dụng — ngoài đợt đề xuất

| Passage ID | Tiêu đề | File local | Giây | Câu RETIRED | Snapshot |
| --- | --- | --- | --- | --- | --- |
| LP-PASS-L-ATTITUDE-B1 | Invitation to a neighborhood clean-up | listening-attitude-b1.mp3 | 17.14 | 36 | 30 |
| LP-PASS-L-DETAIL-B1 | Planning a community fair | listening-detail-b1.mp3 | 23.30 | 36 | 60 |
| LP-PASS-L-GIST-B1 | Library opening hours announcement | listening-gist-b1.mp3 | 19.18 | 36 | 90 |
| LP-PASS-L-INFERENCE-B1 | A change in the weather | listening-inference-b1.mp3 | 13.71 | 36 | 0 |
| LP-PASS-L-SPECIFIC-B1 | Saturday workshop details | listening-specific-b1.mp3 | 16.44 | 36 | 120 |

Các file cũ có đủ trên máy và đọc được định dạng MP3. Chưa đối chiếu bằng nghe nội dung recording với transcript; việc tên file khớp không đủ để tự đổi snapshot. JSON demo cũ vẫn có URL giả; đây là khác biệt cần giữ riêng khi duyệt.

## Snapshot hiện có — chỉ thống kê, không đề xuất tự sửa

| Passage ID | URL trong snapshot | Số snapshot | Transcript so với passage hiện tại |
| --- | --- | --- | --- |
| LP-PASS-L-ATTITUDE-B1 | https://example.com/mock-audio/listening-attitude-b1.mp3 | 30 | Khớp |
| LP-PASS-L-DETAIL-B1 | https://example.com/mock-audio/listening-detail-b1.mp3 | 60 | Khớp |
| LP-PASS-L-GIST-B1 | /learning-path-audio/listening-gist-b1.mp3 | 30 | Khớp |
| LP-PASS-L-GIST-B1 | https://example.com/mock-audio/listening-gist-b1.mp3 | 60 | Khớp |
| LP-PASS-L-SPECIFIC-B1 | /learning-path-audio/listening-specific-b1.mp3 | 30 | Khớp |
| LP-PASS-L-SPECIFIC-B1 | https://example.com/mock-audio/listening-specific-b1.mp3 | 90 | Khớp |

Có 300 snapshot Listening trong tổng 480 snapshot của 7 lộ trình. Transcript khớp passage cũ không chứng minh file local đã được nghe duyệt; không tự ánh xạ sang bộ B1-V2 vì là nội dung khác. Thay passage ACTIVE không tự sửa các snapshot này.

## JSON nguồn và khả năng tái tạo dữ liệu

- `tools/learning-path-authoring/b1-replacement/b1-replacement-draft.json`: có 30 passage Listening B1-V2, nội dung và audio.url khớp DB; status trong bundle vẫn DRAFT theo chủ đích, DB là ACTIVE. Khi di chuyển phải cập nhật tham chiếu Media trong bundle, không biến cả bộ nguồn thành ACTIVE.
- `tools/learning-path-authoring/b1-replacement/parts/L-01.json` đến `L-30.json`: nội dung khớp; parts chứa title/stimulus và câu hỏi, không chứa audio URL.
- `tools/mongo-json-importer/samples/learning-path-demo.json`: chứa bộ cũ, các snapshot mẫu và URL giả. Không thay toàn bộ bằng B1-V2 hoặc import replace đè tiến độ. Nếu muốn sửa demo legacy, phải duyệt một phạm vi riêng.
- `assemble_b1_replacement.py` hiện chỉ giữ trường audio từ bundle cũ; `generate_b1_audio.py --existing-only` ghi lại URL local. Cần điều chỉnh hoặc bảo vệ luồng tái tạo ở bước triển khai để không mất `audio_media_id` sau khi di chuyển. Hiện chưa sửa các công cụ này.
- DB-only DRAFT gắn Media chưa có trong JSON nguồn. Cần thống nhất cách chia sẻ metadata `media_assets` và tham chiếu, kèm cấu hình cùng Cloudinary; không lưu signed URL hay thông tin đăng nhập vào JSON.

## Tự rà soát trước khi gửi duyệt

| Kiểm tra | Kết quả |
| --- | --- |
| Tổng passage Listening | 36 |
| Trạng thái passage | 1 bản nháp / 5 ngừng dùng / 30 đang dùng |
| File MP3 local | 35 |
| File tham chiếu bị thiếu | 0 |
| File trùng nội dung SHA-256 | 0 |
| File chưa có passage tham chiếu | 0 |
| File không đọc được stream MP3 | 0 |
| Passage ACTIVE lệch nội dung bundle | 0 |
| Passage ACTIVE lệch URL nguồn | 0 |
| Passage ACTIVE lệch nội dung parts | 0 |
| Nhóm snapshot lệch transcript passage cũ | 0 |
| Snapshot Listening | 300 |
| Snapshot dùng URL giả | 240 |
| Snapshot dùng URL local | 60 |
| File 001 khớp biên nhận upload | Đạt |
| DRAFT đã upload khớp nội dung ACTIVE 001 | Đạt |
| File cần upload mới nếu được duyệt | 29 |
| Câu hỏi ACTIVE tham chiếu bộ B1-V2 | 150 |

SHA-256 đầy đủ, kích thước byte, codec và ánh xạ từng dòng nằm trong [manifest dữ liệu](learning-path-audio-mapping-2026-11-27.json). Đã kiểm tra tồn tại file, giới hạn đường dẫn trong public, trùng hash/URL, đủ danh sách, nội dung nguồn và Media tái sử dụng. Không chạy test ứng dụng vì không thay code. Chưa nghe duyệt toàn bộ audio; không tạo Media ID giả cho 29 file chưa upload.

## Phạm vi chờ chủ dự án duyệt

Đề xuất đợt tới chỉ xử lý 30 passage ACTIVE B1-V2: dùng lại M01 cho 001, upload mới 002–030 và cập nhật tham chiếu passage/nguồn JSON theo bản sao dự phòng. Không tự đổi trạng thái hoặc ghi xác nhận duyệt mới. API hiện chỉ cho sửa DRAFT; cách cập nhật ACTIVE/revision phải chốt trong phương án di chuyển có kiểm tra đồng thời và khôi phục, không coi phép ánh xạ là xuất bản mới. 5 passage RETIRED, 300 snapshot cũ và DRAFT hiện có nằm ngoài đợt này. Việc cập nhật metadata Media/JSON và chống công cụ sinh lại URL local phải nằm trong kế hoạch triển khai, chưa được thực hiện trong bảng này.
