> Số liệu hiện hành sau bổ sung23/11/2026: xem [kết quả coverage](grammar-coverage-result.md); các số liệu của đợt triển khai bên dưới là hồ sơ lịch sử.

# Grammar — chuẩn hóa tên và cập nhật học thuật local

Ngày hoàn tất: 23/11/2026. Chủ dự án đồng ý tiếp tục phương án tên song ngữ. Đã áp dụng vào hai checkout D của EngoLearnApp và MongoDB dev đang dùng, không chỉ lưu bản đề xuất.

## Kết quả

- Giữ bộ chuẩn 32 chủ đề, 65 bài, 263 khối, 325 câu; không nhập 40 chủ đề ZIP chồng lên dữ liệu.
- Chuẩn hóa tên theo cấu trúc ngữ pháp có thật được dạy trong bài. title_en và title_vi vẫn là hai trường riêng, không thêm model/collection hoặc lưu chuỗi ghép vào trường tiếng Việt.
- FE học viên ghép tên dạng **Present Simple (Thì hiện tại đơn)**: tên Anh nổi bật, tên Việt nhẹ hơn trong ngoặc; tên dài xuống dòng. Admin giữ hai ô tên riêng, đọc đúng dữ liệu đã đổi.
- Áp dụng các sửa học thuật đã rà cho 5 bài: làm rõ nghĩa phủ định hiện tại đơn; xác định mục đích hỏi khả năng với can; giới hạn quy tắc câu hỏi Wh không làm chủ ngữ; sửa lời giải our editor và advise cho khớp câu hỏi.
- Live Admin API: sửa tên 31 Topic (Present Simple đã đúng nên không ghi lại), cập nhật 65 Lesson. Ba bài A1 đã xuất bản được sửa nháp rồi apply thành 3 revision mới qua service thật. Không sửa revision cũ.
- Giữ 1 Topic A1/3 bài PUBLISHED, 31 Topic còn lại DRAFT. Không tự xuất bản toàn bộ học liệu. Học viên ở /grammar vẫn chỉ thấy mẫu A1; xem đủ bộ ở /admin/grammar.

## Bảo toàn dữ liệu

Đã sao lưu dữ liệu Grammar trước cập nhật ngoài repo và đối chiếu lại sau ghi qua FE proxy → BE → MongoDB thật. UUID/code/cấp độ/thứ tự/trạng thái/sẵn sàng/ngày xuất bản lần đầu không đổi. Trước bước mở bài kiểm tra bằng browser, toàn bộ bản ghi tiến độ, phiên luyện tập và level unlock cũ không thay đổi; revision cũ giữ nguyên từng trường. Khi mở bài mới bằng UI, endpoint seen vẫn ghi phiên bản đã xem theo nghiệp vụ bình thường, không đặt lại điểm hoàn thành hoặc bestScore.

topic_sequence giữ 33. ordering_revision tăng 31 là hoạt động khóa thứ tự bình thường của updateTopic → lockTopicOrder(false), không phải phát sinh 31 chủ đề hay đổi thứ tự. Kiểm tra ban đầu giả định mọi trường counter bất biến đã được sửa theo đúng cơ chế service; không sửa BE để né kiểm tra.

Không đụng Placement, module Topic của nhóm, User, navbar/sidebar, API/schema hoặc Java BE. Không commit/push/merge vào main.

## File thay đổi trong lượt này

BE:
- tools/mongo-json-importer/samples/grammar/grammar-cefr-a1-b2.v2.importer.json — seed dùng chung đã sửa tên/nội dung; vẫn DRAFT cho namespace Grammar trống.
- tools/mongo-json-importer/samples/grammar/README.md — chỉ dẫn tên và trạng thái dữ liệu.
- docs/technical/grammar-cefr-roadmap.md — đối chiếu tên hiện hành.
- docs/technical/grammar-academic-names-result.md — báo cáo này.

FE:
- src/features/grammar/components/GrammarLearningUi.tsx — GrammarName dùng chung trong module.
- src/features/grammar/views/GrammarRoadmapView.tsx — tên trên card.
- src/features/grammar/views/GrammarTopicView.tsx — tên chủ đề và bài học.
- src/features/grammar/views/GrammarTheoryView.tsx — tên bài lý thuyết.
- src/features/grammar/views/GrammarTopicSummaryView.tsx — tên trang/bài trong tóm tắt.
- docs/features/grammar/00-grammar-learning.md, 03-academic-names.md và docs/superpowers/plans/2026-11-22-grammar-academic-names.md — quy tắc và kế hoạch tương ứng.

## Kiểm tra thực tế

- Production DTO + GrammarChecklistValidator + GrammarAnswerNormalizer: 65 bài/325 câu đạt 14/14 kiểm tra mỗi bài.
- Đọc lại Admin API đủ 32 chủ đề/65 bài, tên khớp seed. Không ghi DB trực tiếp, không bulk replace.
- npm run build đạt. npm run lint: 0 lỗi, 72 cảnh báo cũ ngoài Grammar; build có cảnh báo kích thước bundle.
- Browser localhost:3000: đã xem roadmap → chủ đề → lý thuyết, tiêu đề hiện Anh (Việt) và không lặp thêm dòng tên Anh. Đã nhìn giao diện ở viewport hiện có; chưa kiểm tra riêng mọi kích thước điện thoại.
- Không chạy lại toàn bộ Maven verify: lượt này không sửa Java/config BE. Không dùng kết quả kiểm tra cấu trúc để tuyên bố bảo đảm học thuật tuyệt đối hoặc toàn app không lỗi.

## Xem local

Mở http://localhost:3000/grammar, đăng nhập tài khoản hiện có và mở Present Simple. Admin xem đầy đủ bộ Nháp tại http://localhost:3000/admin/grammar.

BE/FE được bật từ đúng thư mục D để xem ngay. BE đang giữ port 8080; nếu muốn chạy lại EngolearnApplication trong IDE, cần dừng phiên BE đang chạy trước để tránh lỗi cổng trùng. Không cần import lại seed lên DB local này. Seed dùng chung chỉ dành cho namespace Grammar trống, theo README importer; DB có tiến độ phải cập nhật qua Admin.

## Tên chủ đề hiện hành

Những nhóm gồm nhiều cấu trúc dùng tên bao quát có thật; bài con xác định cấu trúc cụ thể. Ví dụ Future Forms là nhóm diễn đạt tương lai, không tuyên bố going to là một thì mới. Các cấp học lại cấu trúc cũ ở mức sâu hơn vẫn giữ tên chuẩn, khác mục tiêu/bài tập/level. Tên Việt là lựa chọn biên tập thông dụng của dự án; không tuyên bố CEFR bắt buộc một bản dịch hay một danh sách ngữ pháp duy nhất.

| Cấp | Mã | Tên Anh | Tên Việt |
|---|---|---|---|
| A1 | ENG-GRM-T0003 | Subject Pronouns and Be | Đại từ nhân xưng làm chủ ngữ và động từ be |
| A1 | ENG-GRM-T0004 | Nouns, Articles and Possessives | Danh từ, mạo từ và sở hữu |
| A1 | ENG-GRM-T0001 | Present Simple | Thì hiện tại đơn |
| A1 | ENG-GRM-T0005 | There Is/There Are and Prepositions of Place | Cấu trúc there is/there are và giới từ chỉ nơi chốn |
| A1 | ENG-GRM-T0006 | Can and Imperatives | Động từ khuyết thiếu can và câu mệnh lệnh |
| A1 | ENG-GRM-T0007 | Present Continuous | Thì hiện tại tiếp diễn |
| A1 | ENG-GRM-T0008 | Past Simple | Thì quá khứ đơn |
| A1 | ENG-GRM-T0009 | Wh-Questions and Conjunctions | Câu hỏi có từ để hỏi và liên từ |
| A2 | ENG-GRM-T0010 | Past Continuous and Past Simple | Thì quá khứ tiếp diễn và quá khứ đơn |
| A2 | ENG-GRM-T0011 | Present Perfect | Thì hiện tại hoàn thành |
| A2 | ENG-GRM-T0012 | Future Forms | Các cách diễn đạt tương lai |
| A2 | ENG-GRM-T0013 | Comparatives, Superlatives, Too and Enough | So sánh hơn, so sánh nhất, too và enough |
| A2 | ENG-GRM-T0014 | Quantifiers | Lượng từ |
| A2 | ENG-GRM-T0015 | Modal Verbs and Have To | Động từ khuyết thiếu và cấu trúc have to |
| A2 | ENG-GRM-T0016 | Defining Relative Clauses | Mệnh đề quan hệ xác định |
| A2 | ENG-GRM-T0017 | Zero and First Conditionals | Câu điều kiện loại 0 và loại 1 |
| B1 | ENG-GRM-T0018 | Present Perfect and Past Simple | Thì hiện tại hoàn thành và quá khứ đơn |
| B1 | ENG-GRM-T0019 | Past Perfect and Used To | Thì quá khứ hoàn thành và cấu trúc used to |
| B1 | ENG-GRM-T0020 | Passive Voice | Câu bị động |
| B1 | ENG-GRM-T0021 | Defining Relative Clauses | Mệnh đề quan hệ xác định |
| B1 | ENG-GRM-T0022 | Reported Speech | Câu tường thuật |
| B1 | ENG-GRM-T0023 | First and Second Conditionals | Câu điều kiện loại 1 và loại 2 |
| B1 | ENG-GRM-T0024 | Modal Verbs | Động từ khuyết thiếu |
| B1 | ENG-GRM-T0025 | Verb Patterns: -ing and To-Infinitives | Mẫu động từ: dạng -ing và nguyên mẫu có to |
| B2 | ENG-GRM-T0026 | Perfect and Future Tenses | Các thì hoàn thành và tương lai |
| B2 | ENG-GRM-T0027 | Third and Mixed Conditionals | Câu điều kiện loại 3 và câu điều kiện hỗn hợp |
| B2 | ENG-GRM-T0028 | Reporting Passive and Causatives | Bị động tường thuật và cấu trúc nhờ làm |
| B2 | ENG-GRM-T0029 | Non-defining Relative Clauses and Prepositions | Mệnh đề quan hệ không xác định và giới từ |
| B2 | ENG-GRM-T0030 | Reporting Verbs | Động từ tường thuật |
| B2 | ENG-GRM-T0031 | Modal Verbs with Have + Past Participle | Động từ khuyết thiếu với have + quá khứ phân từ |
| B2 | ENG-GRM-T0032 | Verb Patterns: Changes in Meaning | Mẫu động từ: thay đổi nghĩa |
| B2 | ENG-GRM-T0033 | Conjunctions and Participle Clauses | Liên từ và mệnh đề phân từ |

Nguồn đối chiếu tên/cấu trúc: [British Council — Present simple](https://learnenglish.britishcouncil.org/free-resources/grammar/a1-a2/present-simple), [Question forms](https://learnenglish.britishcouncil.org/free-resources/grammar/a1-a2/question-forms). Các căn cứ học thuật khác được giữ trong ghi chú nguồn của bộ học liệu và báo cáo review trước đó.
