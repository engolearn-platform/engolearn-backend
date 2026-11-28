# Di chuyển audio Learning Path sang Cloudinary — 27/11/2026

> Đây là ghi nhận đợt chuyển 30 passage ACTIVE. Sau đó chủ dự án đã yêu cầu
> chuyển cả snapshot cũ; [đợt 300 snapshot Listening](learning-path-legacy-audio-migration-2026-11-27.md)
> đã hoàn tất riêng. Các câu “giữ nguyên snapshot” dưới đây mô tả đúng phạm vi đợt ACTIVE này.

Trạng thái: **đã thực hiện theo phạm vi chủ dự án duyệt**. Nhánh backend/frontend: `fix/learning-path-media-with-cloudinary`. Chỉ chuyển audio của 30 passage ACTIVE B1-V2; không thay nội dung hoặc xuất bản lại.

## Kết quả

- Upload mới đúng 29 MP3 qua API tạo passage hiện có; dùng lại Media của file 001. Không gọi upload trực tiếp hoặc tạo file audio thử trên Cloudinary.
- Đã tạo đủ 30 bản ghi `media_assets` purpose `LEARNING_PATH_PASSAGE_AUDIO`, policy `PROTECTED`, MIME `audio/mpeg`.
- Gắn `audio_media_id` vào 30 passage ACTIVE trong một transaction MongoDB có preflight nội dung/revision và kiểm tra Media. Xóa trường `audio` local ở đúng 30 passage đó; tăng revision 1, cập nhật thời gian/người cập nhật. Giữ nguyên trạng thái, title/transcript/skill/level, nội dung khác và `publication_review` hiện có; không tự ghi xác nhận chuyên môn mới.
- Passage 012: revision 2 → 3. Các passage còn lại: 1 → 2.
- 29 DRAFT trung gian chỉ dùng API upload được xóa sau khi Media đã gắn ACTIVE và kiểm tra tải thành công. Không xóa asset Cloudinary hoặc metadata. DRAFT gốc gắn file 001 giữ nguyên.
- So sánh hash BSON của toàn bộ `learning_paths`, `learning_path_questions`, `question_pool` trước/sau: không đổi. Cả 43 passage ngoài phạm vi giữ nguyên toàn bộ dữ liệu. Còn 7 path, 480 snapshot, gồm 300 snapshot Listening cũ.

## Ánh xạ đã áp dụng

| Passage ID | Revision trước → sau | Media ID | File | Kiểm tra |
| --- | --- | --- | --- | --- |
| LP-PASS-L-B1-V2-001 | 1 → 2 | 6d649d45-1f23-4e1b-8a70-5a26802a9327 | listening-001.mp3 | tải 200 / Range 206 / MP3 đạt |
| LP-PASS-L-B1-V2-002 | 1 → 2 | 9b86d3d1-eb36-415a-bdf8-c5a8ed3c2cc9 | listening-002.mp3 | tải 200 / Range 206 / MP3 đạt |
| LP-PASS-L-B1-V2-003 | 1 → 2 | 1a63987d-951d-48c1-80a7-3102d779cd5b | listening-003.mp3 | tải 200 / Range 206 / MP3 đạt |
| LP-PASS-L-B1-V2-004 | 1 → 2 | 843af062-6cb4-48d4-b3d7-468638e7c6ae | listening-004.mp3 | tải 200 / Range 206 / MP3 đạt |
| LP-PASS-L-B1-V2-005 | 1 → 2 | 10fad554-0bfb-4f20-bdff-2b7ffb203b5f | listening-005.mp3 | tải 200 / Range 206 / MP3 đạt |
| LP-PASS-L-B1-V2-006 | 1 → 2 | 560543b1-cfff-4737-8164-89ff415d03e7 | listening-006.mp3 | tải 200 / Range 206 / MP3 đạt |
| LP-PASS-L-B1-V2-007 | 1 → 2 | da9250d7-3e97-4e67-bb28-bb131bd9a23e | listening-007.mp3 | tải 200 / Range 206 / MP3 đạt |
| LP-PASS-L-B1-V2-008 | 1 → 2 | 03c5e46d-5575-4a86-8fec-d3afcf6e9823 | listening-008.mp3 | tải 200 / Range 206 / MP3 đạt |
| LP-PASS-L-B1-V2-009 | 1 → 2 | 7430a6c8-b27a-4968-ba4b-52edbc1039b4 | listening-009.mp3 | tải 200 / Range 206 / MP3 đạt |
| LP-PASS-L-B1-V2-010 | 1 → 2 | cdfe2351-d384-48c6-94b1-9edc4dbab850 | listening-010.mp3 | tải 200 / Range 206 / MP3 đạt |
| LP-PASS-L-B1-V2-011 | 1 → 2 | e8326c22-6102-4b6d-a0f5-9a5ce6c3c95f | listening-011.mp3 | tải 200 / Range 206 / MP3 đạt |
| LP-PASS-L-B1-V2-012 | 2 → 3 | f5a97135-cdb4-4b81-8ace-dfdd31c19613 | listening-012.mp3 | tải 200 / Range 206 / MP3 đạt |
| LP-PASS-L-B1-V2-013 | 1 → 2 | ed31fce5-2b07-4c2a-8057-e09e5a7a97e9 | listening-013.mp3 | tải 200 / Range 206 / MP3 đạt |
| LP-PASS-L-B1-V2-014 | 1 → 2 | a6953a27-efb6-4d7c-bd0f-583b52940832 | listening-014.mp3 | tải 200 / Range 206 / MP3 đạt |
| LP-PASS-L-B1-V2-015 | 1 → 2 | 9a863383-02cb-4575-ad17-41c433d7e5af | listening-015.mp3 | tải 200 / Range 206 / MP3 đạt |
| LP-PASS-L-B1-V2-016 | 1 → 2 | 5ca49cc8-0553-4e82-bbae-54c5ab533ee0 | listening-016.mp3 | tải 200 / Range 206 / MP3 đạt |
| LP-PASS-L-B1-V2-017 | 1 → 2 | 413b3952-1b5f-4bb8-b790-e40fbc187208 | listening-017.mp3 | tải 200 / Range 206 / MP3 đạt |
| LP-PASS-L-B1-V2-018 | 1 → 2 | cf55b33c-d0dc-4d66-942a-0fbd8e7ab384 | listening-018.mp3 | tải 200 / Range 206 / MP3 đạt |
| LP-PASS-L-B1-V2-019 | 1 → 2 | b5c6c09c-6083-4aac-9edf-d20a437390f2 | listening-019.mp3 | tải 200 / Range 206 / MP3 đạt |
| LP-PASS-L-B1-V2-020 | 1 → 2 | 59701bd8-1b4d-4697-b2c6-a0b74dca18be | listening-020.mp3 | tải 200 / Range 206 / MP3 đạt |
| LP-PASS-L-B1-V2-021 | 1 → 2 | d3c54d23-9ac8-4f7b-836a-a75f5a455223 | listening-021.mp3 | tải 200 / Range 206 / MP3 đạt |
| LP-PASS-L-B1-V2-022 | 1 → 2 | 781e0d83-712b-4e1c-8b91-258af07a3550 | listening-022.mp3 | tải 200 / Range 206 / MP3 đạt |
| LP-PASS-L-B1-V2-023 | 1 → 2 | dfb669fa-ed35-41ba-aae9-414005205ee4 | listening-023.mp3 | tải 200 / Range 206 / MP3 đạt |
| LP-PASS-L-B1-V2-024 | 1 → 2 | 238a3005-1177-44e6-a4ed-e204bc655522 | listening-024.mp3 | tải 200 / Range 206 / MP3 đạt |
| LP-PASS-L-B1-V2-025 | 1 → 2 | 9d12dd56-b28b-41f0-88ae-4d9835348550 | listening-025.mp3 | tải 200 / Range 206 / MP3 đạt |
| LP-PASS-L-B1-V2-026 | 1 → 2 | 01d75cc1-be91-484c-9a07-888663c86476 | listening-026.mp3 | tải 200 / Range 206 / MP3 đạt |
| LP-PASS-L-B1-V2-027 | 1 → 2 | ea9145a1-9966-4d36-b750-0703e7a724bf | listening-027.mp3 | tải 200 / Range 206 / MP3 đạt |
| LP-PASS-L-B1-V2-028 | 1 → 2 | 2a14056a-7ded-4335-9e2a-6b71b0ab8bce | listening-028.mp3 | tải 200 / Range 206 / MP3 đạt |
| LP-PASS-L-B1-V2-029 | 1 → 2 | 6acdb8f3-3e5d-4124-b2c7-51116c234ae3 | listening-029.mp3 | tải 200 / Range 206 / MP3 đạt |
| LP-PASS-L-B1-V2-030 | 1 → 2 | e218adcb-e2d5-417f-9b2d-dbd307b80025 | listening-030.mp3 | tải 200 / Range 206 / MP3 đạt |

## Dữ liệu JSON và công cụ tái tạo

- Theo yêu cầu chủ dự án, bộ B1-V2 đã được gộp vào một file chính [learning-path-demo.json](../../../tools/mongo-json-importer/samples/learning-path-demo.json): gồm dữ liệu demo cũ, thêm 60 passage DRAFT/300 câu hỏi DRAFT và 30 Media metadata. Không dùng replace để đè DB đang có tiến độ. Không cần import file B1 hoặc Media riêng.

- Metadata của 30 Media B1 nằm ngay trong `learning-path-demo.json`, không có signed URL hoặc thông tin đăng nhập. Dùng cùng tài khoản Cloudinary đang chứa các file này.
- [Manifest ghi âm và ánh xạ](data/learning-path-b1-cloudinary-mapping.json): Media ID, đường dẫn file gốc, SHA-256 file/transcript, thời lượng và kích thước. `sourceUrl` trong manifest là bằng chứng file local, không phải URL dùng để phát hoặc fallback.
- `tools/learning-path-authoring/b1-replacement/b1-replacement-draft.json`: 30 passage Listening chuyển sang Media ID, bổ sung 30 metadata Media. Giữ nguyên 300 câu hỏi và trạng thái DRAFT của 60 passage nguồn. JSON nguồn DRAFT không phải bản sao trạng thái ACTIVE của DB.
- `tools/learning-path-authoring/b1-replacement/audio-media-manifest.json`: nguồn đối chiếu cho công cụ tái tạo.
- `assemble_b1_replacement.py` giữ Media ID khi transcript không đổi và giữ metadata liên quan; transcript đổi không được tự dùng lại recording cũ.
- `generate_b1_audio.py --existing-only` giữ nguyên Media ID; kiểm tra hash file/transcript để từ chối recording thay đổi. Không ghi đè URL local lên passage đã có Media.
- `validate_b1_replacement.py` kiểm tra cả Media metadata/provenance và file gốc; tài liệu review hiển thị Media ID.
- Không import replace nguyên bundle DRAFT lên DB đang dùng: có thể đổi trạng thái/nội dung/tiến độ ngoài phạm vi. Thành viên cần Media metadata và dữ liệu passage cùng tham chiếu; import metadata trùng ID phải kiểm tra xung đột trước. Không đổi importer toàn cục sang replace.
- Chưa commit hoặc push. Theo yêu cầu gộp dữ liệu, file chia sẻ chính hiện là `tools/mongo-json-importer/samples/learning-path-demo.json`; không cần đưa toàn bộ công cụ authoring vào commit để chia sẻ dữ liệu này. Các manifest trong docs chỉ là bằng chứng đối chiếu, không phải file import bổ sung.

## Kiểm chứng đã chạy

- 30/30 preview qua API admin trả Media đúng ID và URL chưa hết hạn. Tải toàn bộ MP3 từ Cloudinary HTTP 200; Range bytes=2000–3999 HTTP 206, đúng đoạn của file tải về.
- FFprobe đối chiếu thời lượng file Cloudinary với file gốc (lệch dưới 0,15 giây), xác nhận MP3; FFmpeg giải mã cả 30 file không lỗi. Đây là kiểm tra kỹ thuật, không thay cho người nghe duyệt chất lượng/nội dung.
- Readiness của passage ACTIVE vẫn có lỗi trạng thái DRAFT theo thiết kế; không còn lỗi audio/Media hoặc lỗi chặn capability.
- Kiểm tra bundle bằng validator thật trong thư mục tạm: 60 passage, 300 câu hỏi, 30 audio, 0 lỗi/0 editorial flag. Không ghi đè tài liệu review đã có.
- Chạy assemble và existing-only trên bản sao nguồn: giữ đủ 30 Media ID/metadata; dữ liệu sau existing-only không đổi. Thay transcript trong bản sao bị từ chối dùng recording cũ.
- Backend HTTP/Mongo/Cloudinary thật trên DB tạm riêng: tạo path mới, start 10 câu, cấp lại audio (no-store, Range 206), lưu đáp án, submit, review và cấp lại audio review đều đạt. Snapshot đủ 30/30 `audio_media_id`, không có `audio`/URL tạm; start không lộ transcript/đáp án, review giữ đáp án đã chọn.
- DB kiểm thử riêng sử dụng bản sao account demo, assessment READY và 30 câu hỏi Gist/passages thật sau migration, kèm 30 Media. Không tạo path kiểm thử trong DB nhóm. Path kiểm thử: `6b097bf6ca7b9674d495a2e1`.
- Không sửa code ứng dụng Java/frontend trong đợt migration này; không chạy lại bộ Maven hoặc browser suite. Kết quả 435 test và browser playback/expiry ở [bước 5](learning-path-media-verification.md) là bằng chứng trước migration; các kiểm tra dữ liệu/HTTP/provider nêu trên chạy sau migration.

## Sao lưu và khôi phục

Bản sao local được lưu trong `.artifacts/learning-path-media-migration-20261127/` (được gitignore), không nằm trong `target/`:

- `before.json`: 30 passage trước đổi, 43 passage loại trừ, Media cũ, hash các collection nghiệp vụ.
- `uploaded-records.json`: metadata 30 Media và 29 DRAFT trung gian để đối chiếu/khôi phục tham chiếu nếu cần.
- `after.json`, `uploads.json`, `delivery-report.json`, `learner-flow-report.json`, `final-audit.json`: biên nhận và bằng chứng; không chứa cookie hay signed URL.
- `source-before/`: bundle nguồn và các công cụ trước sửa.

Khôi phục cần kiểm tra đúng DB, revision và dữ liệu hiện tại so với `after.json` trước khi áp dụng `before.json` trong transaction. Không replace đè bản ghi đã được người khác sửa; không xóa asset Cloudinary đang có snapshot hoặc passage tham chiếu. Nguồn JSON có bản sao riêng để phục hồi sau khi kiểm tra thay đổi đồng thời. Chưa chạy thao tác khôi phục.

## Phần giữ riêng

Các backend tạm phục vụ upload/kiểm tra đã được dừng; container MongoDB kiểm thử riêng đã được dọn. MongoDB nhóm vẫn được giữ hoạt động. Audio Cloudinary, metadata và bản sao local được giữ nguyên.

5 passage RETIRED và 300 snapshot Listening cũ không được chuyển. 240 snapshot dùng URL giả vẫn chưa nghe được; 60 snapshot local vẫn phụ thuộc file local. Các MP3 trong frontend public được giữ nguyên. Muốn xử lý các snapshot này cần duyệt riêng đúng recording của bộ cũ, giữ nội dung/đáp án/tiến độ.
