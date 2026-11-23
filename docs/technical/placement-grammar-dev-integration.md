> Cập nhật bàn giao cuối23/11/2026: cấu hình hiện dùng MONGODB_URI ở cả dev/prod, không dùng override spring.mongodb.uri trong .env. Placement dùng [gói16 đề/320 câu](../../tools/mongo-json-importer/samples/placement/README.md) thay cho việc nhập chồng các gói cũ;7 Published/9 Draft. Grammar local39 chủ đề/99 bài Published,4 chủ đề/7 bài Nháp; seed Grammar vẫn toàn Nháp. Các số liệu và lệnh migration phía dưới là hồ sơ đợt tích hợp trước, không chạy lại thay cho hướng dẫn gói hiện hành.

# Placement + Grammar: tích hợp dev của nhóm — 23/11/2026

## Nguồn chuẩn và phạm vi

Main đã fetch: BE `0f41690`, FE `59078cc`; chưa có bộ hợp đồng mới. Người phụ trách đã xác nhận lấy dev vào đúng nhánh hiện tại: BE `merge/placement-guest-user-main`, FE `fix/placement-guest-navbar`. Mốc bảo toàn trước merge: BE `32c72f3`, FE `3bb7fba`; merge local: BE `18e7757`, FE `cc7ab93`, nguồn dev BE `96c7989`, FE `0e9adee`. Không tạo nhánh mới, không push main/dev.

3 xung đột BE được kết hợp theo hành vi: SecurityConfig giữ cả Speaking lẫn Placement; MongoPlacementTestSetRepository giữ cách chọn đề của Placement và cấm fallback Nháp khi có prod; ArchitectureTest giữ Grammar cùng Media/Speaking mới. FE GuestOnlyGuard theo cách điều hướng sau đăng nhập của nhóm. Code module khác vào từ merge; thay đổi tích hợp chủ động chỉ trong Placement/Grammar, matcher SecurityConfig và tài liệu liên quan.

## Quyền và hồ sơ

- Placement Admin: ADMIN. Grammar Admin: CONTENT_MANAGER (ADMIN kế thừa). Grammar learner/Placement kết quả và lệnh bắt đầu học: USER (hai vai trò cao hơn kế thừa). Guest vẫn có thể bắt đầu/trả lời/nộp Placement, nhưng cần đăng nhập và nhận quyền sở hữu kết quả trước khi xem kết quả/review.
- `@PreAuthorize` nằm trên entry point của service Spring; HTTP matcher đặt trước public Placement. Không áp role lên bộ chấm/đếm hạn nội bộ vì Guest và tác vụ tự động vẫn cần dùng chúng.
- FE dùng `ProtectedRoute`/`HasRole` qua `@features/auth/contract`. GrammarAdminGuard chỉ còn nhiệm vụ ghép ManagementLayout; nhóm không có shell mới. Giữ reset màn học theo identityGeneration.
- Lỗi kế thừa ở PlacementLearningController đã được test RED 403 rồi sửa: bỏ so sánh đúng tên USER, dùng guard USER tại service; ownership không đổi.
- Level vẫn lưu tại `user_profiles.cefr_level`, do UserLevelPromotion thuộc User cập nhật. Đề/điểm thuộc Placement; không có profile song song. Chỉ nâng lên max(level hiện tại, level bài thi), cùng transaction với hoàn thành/claim; không hạ C1/C2, không upsert hồ sơ thiếu, không reset tiến độ.
- Code đồng bộ level đã tồn tại trên dev. Xem lại kết quả lịch sử không tự backfill; chủ sở hữu gọi POST `/api/v1/placement/attempts/{id}/learning` để đối chiếu lại. Hãy kiểm tra cùng DB, profile và hồ sơ được tạo từ đăng ký thật khi máy khác không thấy thay đổi. Không sửa Topic hay quy tắc mở khóa Grammar để che vấn đề cấu hình.

## Audio và dữ liệu dùng chung

MediaManagement sở hữu Cloudinary và `media_assets`. Placement chỉ lưu `audio_media_id` ở question, `audio.media_id` ở snapshot; `placement_audio_assets` là nhật ký SHA-256 → mediaId/duration/title/status. Khi trả bài, service kiểm tra quyền/chủ sở hữu trước rồi tạo bản sao response với URL; không lưu URL delivery vào snapshot mới. URL legacy cũ giữ lại để đối chiếu, prod luôn ưu tiên mediaId và từ chối snapshot chưa chuyển.

Chính sách audio PUBLIC giữ nguyên khả năng truy cập audio Guest trước đây; không phải chọn từ request trình duyệt. Chỉ ADMIN được upload, xem catalog và nối tài nguyên. Đây là audio luyện đánh giá đầu vào, không coi URL là cơ chế bảo vệ đáp án. Câu hỏi/đáp án/kết quả vẫn có guard riêng.

- 96 WAV đã upload thật qua Media/Cloudinary. Kiểm tra HTTP Range trả 206, audio/wav, Accept-Ranges bytes.
- Nối cả đường dẫn `/api/v1/placement/media/<sha>.wav` và legacy V1 `http://127.0.0.1:8892/audio/<sha>.wav`, chỉ khi SHA khớp; không tải URL tùy ý.
- Trước/sau migration đã đối chiếu 320 question, 4 đề cũ, 45 attempt, 36 hồ sơ, 43 Grammar Topic, 106 Lesson và toàn bộ progress/session/unlock/published revisions cũ. Ngoài tham chiếu media và revision CAS, dữ liệu giữ nguyên. Snapshot V1/V2/V3 đều có mediaId.
- Bro duyệt xuất bản riêng một bộ V3 demo: `PLT-E5C04B556E0D`, id `e5c04b55-6e0d-44d2-907a-90af890e467e`, 20 câu từ V3 hiện có, 19 câu Nháp chuyển ACTIVE và 1 câu vốn ACTIVE. Các đề cũ vẫn Nháp. Grammar có mẫu A1 6 bài published ngay từ snapshot đầu đợt này, 100 bài Nháp; không thực hiện lệnh xuất bản Grammar trong đợt tích hợp.

File dùng chung `tools/mongo-json-importer/samples/placement-media-cloudinary.importer.json` chứa 96 `media_assets` + 96 `placement_audio_assets` READY, không chứa khóa hoặc dữ liệu người học. Đây là metadata kho Media, không phải bản thứ hai của ngân hàng câu hỏi. Giữ seed Placement hiện tại làm nguồn câu hỏi/đề. Thành viên nhập cả metadata và seed rồi chạy lệnh nối bằng Admin; không cần upload lại. Grammar vẫn dùng gói canonical 3 collection trong `samples/grammar/`.

## Nạp/chuyển audio và phục hồi

Ưu tiên DB dev mới hoặc sao lưu trước khi thao tác. Importer chung có dry-run và policy skip; dùng `-File` để chọn đúng tệp, không nhập mọi dữ liệu mẫu nếu không cần. Không dùng replace để ghi đè tiến độ/attempt. Ví dụ từ BE root:

```powershell
.\tools\mongo-json-importer\import.ps1 -File .\tools\mongo-json-importer\samples\placement-media-cloudinary.importer.json -DryRun
.\tools\mongo-json-importer\import.ps1 -File .\tools\mongo-json-importer\samples\placement-media-cloudinary.importer.json
$management = Get-Credential
.\tools\placement\import-media.ps1 -Credential $management
.\tools\placement\import-media.ps1 -Credential $management -Apply
```

Công cụ cần PowerShell 7+. Trong tools/mongo-json-importer/.env chọn MONGO_IMPORT_CONFLICT_POLICY=skip khi cần giữ bản đã có; policy fail chủ động trả exit 1 khi trùng ID và không ghi gì. Lệnh cuối dùng quyền Admin thật. Mặc định công cụ chỉ preflight, không upload/nối. Với metadata READY đã import, Apply không đọc WAV local: chỉ nối tham chiếu trong Mongo. Khi chưa có metadata, công cụ kiểm tra SHA của toàn bộ file cần thiết trước upload, upload qua endpoint riêng Placement, rồi nối. Gọi lại sau thành công không upload hoặc nối trùng.

Upload lưu nhật ký UPLOADING trước thao tác ngoài DB. READY được tái sử dụng; FAILED có thể thử lại. Nếu tiến trình chết hoặc kết quả ghi nhật ký không rõ, UPLOADING bị chặn 409, không tự upload lần nữa. Operator đối chiếu hash/mediaId trong lỗi nhật ký với metadata Media/provider rồi sửa đúng record nhật ký thuộc Placement sang READY, hoặc xác nhận chưa có asset trước khi cho FAILED; không đoán, không xóa asset bài cũ. Media Phase 1 chưa có API xóa/orphan cleanup. Nếu upload thành công nhưng ghi nhật ký hỏng, log chỉ ghi hash/mediaId để hỗ trợ khôi phục, không ghi khóa. Không cam kết thao tác Cloudinary + Mongo là một transaction.

## Chạy local và prod

Chỉ chọn một runtime profile `dev` hoặc `prod`; không lưu spring.profiles.active trong .env. application.yaml dùng chung cho nhóm, .env đã bị Git ignore. Trong IDE chọn Active profiles=dev khi Run EngolearnApplication; terminal tương đương:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
# FE, terminal riêng tại engolearn-frontend:
npm run dev
```

Chỉ chạy một BE ở 8080. Nếu đã có BE đang chạy thì dùng nó hoặc dừng đúng tiến trình trước khi Run lại; không đổi cổng ngẫu nhiên để che lỗi trùng tiến trình.

Cả dev và prod dùng Mongo replica set thật, transaction và cookie/JWT thật. Dev cho phép helper opt-in; không tự seed. Prod cần dữ liệu Published, mediaId/Cloudinary, HTTPS cho secure cookie, biến DB/CORS/secret hợp lệ; không đọc JSON hay WAV trong repo. Grammar hiện là học liệu chữ, chưa thêm nghe. Không khôi phục profile placement-local/subscription-local.

Máy này riêng biệt: Mongo Windows giữ 27017, Mongo Docker dự án giữ 27019, replica quảng bá localhost:27017. Đã không dừng Mongo Windows hoặc xóa volume Docker. .env local có override spring.mongodb.uri với directConnection=true tới đúng primary Docker 27019 và replicaSet=engolearn-rs; transaction vẫn thật. Máy nhóm dùng Compose chuẩn 27017 không cần override này. Không copy nguyên .env máy này sang máy khác/deployment.

Cloudinary chỉ cấu hình khóa trong .env local, không đưa giá trị vào .env.example/Git. Không cần đăng nhập web Cloudinary khi chạy app. Speaking trên máy này thiếu khóa provider nên tắt wiring Speaking bằng cấu hình local, không sửa code Speaking. Giới hạn Speaking/Assessment prod của nhóm vẫn còn; báo cáo này không chứng nhận các module đó.

## Bằng chứng kiểm tra

Xem phần Kết quả cuối bổ sung sau khi hoàn tất verify và smoke thực tế. Các kiểm tra Auth mô phỏng của nhóm được chạy theo yêu cầu, nhưng không được dùng thay cho chứng cứ tích hợp thật FE proxy → BE → Mongo/Cloudinary.

## Tạo đề Published trên máy thành viên mới

Seed câu hỏi/đề được giữ Nháp để mỗi môi trường duyệt trước. Nhập metadata audio không tự xuất bản đề. Sau khi nhập seed Placement, metadata Cloudinary và chạy nối: đăng nhập Admin → Ngân hàng câu hỏi → kích hoạt đúng 20 ID có trong snapshot V3 `PLT-V3-DEV-001-V3` → tạo một bộ đề mới → chọn 20 câu đó → Preview → Xuất bản. Blueprint: A1 4 Reading/1 Listening, A2 4/1, B1 3/2, B2 3/2; tổng 14 Reading/6 Listening. Không sửa trực tiếp status trong Mongo để bỏ kiểm tra xuất bản. Prod không có fallback Nháp, nên bước duyệt/xuất bản là bắt buộc.

## Kết quả kiểm tra cuối

- Full `mvnw.cmd verify` theo cấu hình CI prod trên DB kiểm thử riêng: 877 tests, 12 failures, 52 errors, 32 skipped. Lệnh trả exit 1; không chứng nhận toàn app đạt.
- Placement: 167 tests, 0 failures, 0 errors, 4 skipped. Không xóa assertion hoặc bỏ qua test để lấy kết quả.
- Grammar: 12 tests, 0 failures, 0 errors, 0 skipped. Không xóa assertion hoặc bỏ qua test để lấy kết quả.
- Các ca còn lỗi nằm ngoài hai module, gồm Security/Speaking, Subscription và Topic; danh sách bên dưới để chuyển đúng owner. Không sửa code/test những nghiệp vụ đó.
- FE Auth 21/21 đạt; build đạt; lint 0 errors/72 cảnh báo cũ; còn cảnh báo kích thước bundle.
- Ca lỗi upload ghi đè transcript được tái hiện RED rồi GREEN: test mới gọi FE proxy/BE/Mongo thật với WAV đã có, chỉ giữ chậm phản hồi HTTP thật, không dựng API response. Upload xong chỉ thay audioUrl bằng functional state update. 1/1 đạt, không skip khi chạy có đủ môi trường.
- Dev 59 kiểm tra qua FE proxy 3000 và prod 60 kiểm tra HTTPS trên DB sao riêng/JAR ngoài repo đạt: Guest login/claim A1→A2, User submit A1→A2, lệnh learning lặp, quyền 401/403, ownership 404, login/refresh/logout cookie và Grammar theory→seen→practice 5/5→đọc lại kết quả. Prod có secure cookie và không phục vụ URL WAV local. Prod smoke là API HTTPS của JAR, không phải chứng nhận đã triển khai frontend công khai.
- Browser thật: cùng tài khoản Admin mở được Grammar và Placement; form Listening có upload và 96 audio không lặp; WAV Cloudinary phát thật (duration 6.443583s, currentTime tăng, readyState=4, error=null). HTTP Range riêng trả 206.
- Metadata importer DryRun đọc 2 collection/192 record, không đổi UUID/hash sang ObjectId, errors=[], writeResults=[]. Exit 1 là policy fail từ chối 192 ID đã tồn tại; không ghi DB. Chưa Apply metadata qua importer vào một namespace mới; live metadata đã được tạo bằng Media API thật.
- Review độc lập đọc 10 file tích hợp trọng yếu, tìm lỗi stale draft khi upload; đã sửa và reviewer xác nhận bằng đọc source. Không dùng review tĩnh thay cho kiểm thử runtime.

### Các kiểm tra Placement bỏ qua do điều kiện test

- `fit.iuh.engolearn.placement.infra.PlacementMediaWebConfigTest.rechecksContainmentAfterAnInitiallyValidFileIsReplacedByASymlink`
- `fit.iuh.engolearn.placement.repository.PlacementMongoPersistenceTest.competingPublicationsCommitOnceAndArchiveKeepsTheOriginalSnapshot`
- `fit.iuh.engolearn.placement.repository.PlacementMongoPersistenceTest.rejectsIncorrectLevelMatrixEvenWithFourteenReadingAndSixListening`
- `fit.iuh.engolearn.placement.repository.PlacementMongoPersistenceTest.deactivationAfterQuestionReadPreventsPublishingAStaleActiveSnapshot`

### Các ca lỗi ngoài Placement/Grammar

- `fit.iuh.engolearn.config.SecurityIntegrationTest.anonymousSpeakingRequestRequiresTheSharedSession`
- `fit.iuh.engolearn.config.SecurityProdGuardTest.theDevAuthFilterIsNotRegisteredInProd`
- `fit.iuh.engolearn.config.SecurityProdGuardTest.theDevHeaderIsIgnoredWhenTheProdProfileIsActive`
- `fit.iuh.engolearn.config.SecurityProdGuardTest.publicTopicBrowseStillWorksInProd`
- `fit.iuh.engolearn.config.SpeakingConversationConfigTest.canonicalManagerOnOverridesTheLegacyFlag`
- `fit.iuh.engolearn.config.SpeakingConversationConfigTest.canonicalManagerOffKeepsSpeakingOpenForTheLegacyEvaluator`
- `fit.iuh.engolearn.config.SpeakingConversationConfigTest.checkedInDefaultsExposeSpeakingWithTheConversationManager`
- `fit.iuh.engolearn.config.SpeakingConversationConfigTest.legacyOverridesStillWorkWhenCanonicalPropertiesAreAbsent`
- `fit.iuh.engolearn.speaking.service.ConversationManagerTest.`
- `fit.iuh.engolearn.speaking.service.SpeakingDraftValidatorContractTest.`
- `fit.iuh.engolearn.subscription.AdminConfigurationIntegrationTest.allHttpMethodsEnforceAdminBeforeReadingDataAndRejectForgedFieldsAndFractionalNumbers`
- `fit.iuh.engolearn.subscription.PurchaseFlowIntegrationTest.trialConversionCalendarClampRetainsUsageAndLateFactsCannotRegress`
- `fit.iuh.engolearn.subscription.PurchaseFlowIntegrationTest.vnpayVerifiedFailureStatusCompletesEvenUnlistedResponseCode`
- `fit.iuh.engolearn.subscription.PurchaseFlowIntegrationTest.annualLeapDayAndDelayedSuccessDoNotExtendExpiredPremium`
- `fit.iuh.engolearn.subscription.PurchaseFlowIntegrationTest.refundWindowUsesPinnedPaidAtAndExcludesDeadline(String)[1]`
- `fit.iuh.engolearn.subscription.PurchaseFlowIntegrationTest.refundWindowUsesPinnedPaidAtAndExcludesDeadline(String)[2]`
- `fit.iuh.engolearn.subscription.PurchaseFlowIntegrationTest.vnpayTimeoutReconcilesSignedPaymentWithoutExpiringOrExtending`
- `fit.iuh.engolearn.subscription.PurchaseFlowIntegrationTest.rollbackLeavesNoIntentOrCommandAndExpiredNeverIssuedIntentUnlocks`
- `fit.iuh.engolearn.subscription.PurchaseFlowIntegrationTest.vnpayRefundTimeoutOrOriginalPaymentQueryCannotConfirmRefund`
- `fit.iuh.engolearn.subscription.PurchaseFlowIntegrationTest.activationReceiptFailureRollsBackTrialPeriodPurchaseAndEntitlementOutbox`
- `fit.iuh.engolearn.subscription.PurchaseFlowIntegrationTest.sameIntentReplaysPinnedPriceAndDifferentSelectionConflicts`
- `fit.iuh.engolearn.subscription.PurchaseFlowIntegrationTest.concurrentSuccessFactsCreateOnePeriod()[1]`
- `fit.iuh.engolearn.subscription.PurchaseFlowIntegrationTest.concurrentSuccessFactsCreateOnePeriod()[2]`
- `fit.iuh.engolearn.subscription.PurchaseFlowIntegrationTest.checkoutPreparationAndAuthoritativeExpiryKeepUnknownLocked`
- `fit.iuh.engolearn.subscription.PurchaseFlowIntegrationTest.vnpayQueryErrorsAreObservableAndNeverUnlockUnverifiedPayment`
- `fit.iuh.engolearn.subscription.PurchaseFlowIntegrationTest.refundHttpRequiresAdminStrictBodyAndReplaysStableRequest`
- `fit.iuh.engolearn.subscription.PurchaseFlowIntegrationTest.historyIsOwnerScopedStableAcrossTiesAndConcurrentInsertion`
- `fit.iuh.engolearn.subscription.PurchaseFlowIntegrationTest.mismatchedSuccessGoesToReviewWithoutGrantingOrUnlocking`
- `fit.iuh.engolearn.subscription.PurchaseFlowIntegrationTest.lateSuccessAfterFailurePreservesNewPurchaseLockForReview`
- `fit.iuh.engolearn.subscription.PurchaseFlowIntegrationTest.refundCommandReplayConflictAndSecondFullRefundAreGuarded`
- `fit.iuh.engolearn.subscription.PurchaseFlowIntegrationTest.refundFailureRetryOldFactsAndNaturalExpiryPreserveRights`
- `fit.iuh.engolearn.subscription.PurchaseFlowIntegrationTest.conflictingCommandCannotRejectOrUnlockLegitimatePurchase`
- `fit.iuh.engolearn.subscription.PurchaseFlowIntegrationTest.vnpayConcurrentWorkersPrepareAndSubmitRefundOnce()[1]`
- `fit.iuh.engolearn.subscription.PurchaseFlowIntegrationTest.vnpayConcurrentWorkersPrepareAndSubmitRefundOnce()[2]`
- `fit.iuh.engolearn.subscription.PurchaseFlowIntegrationTest.concurrentDifferentKeysCreateOneBlockingPurchase()[1]`
- `fit.iuh.engolearn.subscription.PurchaseFlowIntegrationTest.concurrentDifferentKeysCreateOneBlockingPurchase()[2]`
- `fit.iuh.engolearn.subscription.PurchaseFlowIntegrationTest.httpAuthenticationStrictBodyReplayAndOwnership`
- `fit.iuh.engolearn.subscription.PurchaseFlowIntegrationTest.historyHttpRejectsAnonymousBadBoundsAndBadCursor`
- `fit.iuh.engolearn.subscription.PurchaseFlowIntegrationTest.refundSuccessIsAtomicRetainsUsageAndDoesNotRestoreTrial`
- `fit.iuh.engolearn.subscription.PurchaseFlowIntegrationTest.refundConcurrentRequestsAndOutcomesProduceOneAttemptAndFact()[1]`
- `fit.iuh.engolearn.subscription.PurchaseFlowIntegrationTest.refundConcurrentRequestsAndOutcomesProduceOneAttemptAndFact()[2]`
- `fit.iuh.engolearn.subscription.PurchaseFlowIntegrationTest.vnpayUnresolvedStatusesDoNotBecomeFailureFromResponseCodeAlone(String)[1]`
- `fit.iuh.engolearn.subscription.PurchaseFlowIntegrationTest.vnpayUnresolvedStatusesDoNotBecomeFailureFromResponseCodeAlone(String)[2]`
- `fit.iuh.engolearn.subscription.PurchaseFlowIntegrationTest.vnpayUnresolvedStatusesDoNotBecomeFailureFromResponseCodeAlone(String)[3]`
- `fit.iuh.engolearn.subscription.PurchaseFlowIntegrationTest.vnpayCheckoutRawIpnRejectsTamperingAndAtomicallyActivatesAtSecondPrecision`
- `fit.iuh.engolearn.subscription.PurchaseFlowIntegrationTest.vnpayRefundProcessingWaitsForSignedFullRefundAndNeverResubmits`
- `fit.iuh.engolearn.subscription.PurchaseFlowIntegrationTest.devResetRetainsUsageAndHistoryAndLateFactsCannotActivateOrReblock`
- `fit.iuh.engolearn.subscription.PurchaseFlowIntegrationTest.devResetHttpEnforcesOwnerAndRejectsPaidFactsAndRollsBackFailure`
- `fit.iuh.engolearn.subscription.SubscriptionSecurityTest.publicCatalogAndPrivateGuestRoutesHaveStableSecurityContract`
- `fit.iuh.engolearn.topic.controller.TopicLearningControllerIntegrationTest.putPositionCommitsOneDocumentPerProgressCollectionAndGetPositionEchoesIt`
- `fit.iuh.engolearn.topic.controller.TopicLearningControllerIntegrationTest.getTopicProgressReportsCountsAndPerItemStatusFromRealMongo`
- `fit.iuh.engolearn.topic.controller.TopicLearningControllerIntegrationTest.getTopicProgressWhenEveryVisibleItemIsCompletedReturnsCompleted`
- `fit.iuh.engolearn.topic.controller.TopicLearningControllerIntegrationTest.getTopicProgressWithOnlyHiddenItemsInManifestReturnsEmptyNotStartedProgress`
- `fit.iuh.engolearn.topic.controller.TopicLearningControllerIntegrationTest.majorUpdatePublishedAfterItemStartKeepsContentAndPositionOnPinnedVersion`
- `fit.iuh.engolearn.topic.controller.TopicLearningControllerIntegrationTest.getContentReturnsFullItemContentFromRealMongo`
- `fit.iuh.engolearn.topic.controller.TopicLearningControllerIntegrationTest.getTopicProgressWithoutAnyRecordsReturnsNotStartedWithNullLastAccessedAt`
- `fit.iuh.engolearn.topic.controller.TopicLearningRollbackIntegrationTest.failureWhileSavingItemProgressRollsBackTheWholePositionWrite`
- `fit.iuh.engolearn.topic.controller.TopicLearningUnauthenticatedIntegrationTest.guestPositionWriteIsRejectedBeforeBodyProcessing`
- `fit.iuh.engolearn.topic.controller.TopicLearningUnauthenticatedIntegrationTest.everyLearningEndpointRejectsAGuestWith401AndWritesNoProgress`
- `fit.iuh.engolearn.topic.service.impl.TopicBrowseServiceImplTest.theCardShowsTheMaintainedTopicDurationAndCountsOnlyVisibleItems`
- `fit.iuh.engolearn.topic.service.impl.TopicBrowseServiceImplTest.detailReturnsTheVisibleItemsOfTheCurrentReleaseInTheirFixedOrder`
- `fit.iuh.engolearn.topic.service.impl.TopicQuizAnswerTest.correctLastQuestionKeepsFrontierAndDoesNotCompleteTheAttempt`
- `fit.iuh.engolearn.topic.service.impl.TopicQuizReadTest.completedAttemptReturnsNullQuizWithoutReadingQuizSequence`
- `fit.iuh.engolearn.topic.service.impl.TopicQuizReadTest.completedAttemptWithHiddenPinnedVersionStillReturnsNullQuiz`

## Bàn giao chạy bằng IDE

Đã kiểm tra lại cùng 119 ca trên JAR cuối từ đúng source sau review. Khi bàn giao đã dừng đúng các tiến trình BE/JAR và FE kiểm thử do mình khởi động, giải phóng 8080/3000/8443; không dừng Mongo Windows/Docker. Bro Run EngolearnApplication với Active profiles=dev (hoặc VM option -Dspring.profiles.active=dev), rồi npm run dev từ FE. Không cần chạy JAR lúc coding.

Kiểm thử thật tạo 4 tài khoản riêng có tiền tố module-smoke, 4 attempt và 2 practice session local; chúng không nằm trong seed hay Git. Mọi bản ghi cũ giữ nguyên; chỉ có tham chiếu/counter kỹ thuật của migration, publication_guard cho 20 câu được duyệt, learner_publication_fence tăng đúng 2 do hai lượt practice mới, và 19 status câu hỏi chuyển ACTIVE theo duyệt. Không mass backfill/reset tiến độ.

## Kiểm tra build sạch và entry point IDE

Phát hiện target còn hai YAML profile cũ từ trước merge dù source đã xóa. Đã lưu báo cáo test rồi clean package lại; JAR cuối chỉ chứa application.yaml, application-dev.yaml và application-prod.yaml. Đây là dọn đầu ra build, không thêm profile hay chỉnh code của module khác.

Đã chạy trực tiếp class EngolearnApplication với dev như cách IDE dùng, và JAR sạch ngoài repo với prod/HTTPS: cả hai login Admin đạt, đọc 43 chủ đề Grammar, bộ Placement Published 20 câu và audio Cloudinary trả Range206. Đã dừng cả hai ngay sau xác nhận. Ba DB/tài khoản DB kiểm thử riêng do đợt này tạo đã được dọn; DB nghiệp vụ, Mongo Docker/Windows và backup riêng tư giữ nguyên.

## Sự cố Run IntelliJ sau bàn giao — 23/11/2026

Log IDE 16:17:04 báo RuntimeProfileInitializer từ chối lựa chọn profile. Run configuration EngolearnApplication vẫn giữ ACTIVE_PROFILES=dev,placement-local; profile placement-local đã bị nhóm loại bỏ. Đã sửa đúng cấu hình Run local bằng IntelliJ thành dev, lưu và Run trực tiếp trong IDE. Console xác nhận Tomcat8080/Started EngolearnApplication; API thật đăng nhập Admin đạt, đọc 43 Grammar Topic, 5 Placement TestSet (1 Published) và catalog96 audio. Không nới initializer hoặc sửa code nghiệp vụ; .idea/workspace.xml bị Git ignore. BE lần này được giữ chạy trong IDE để chủ dự án test.

Phạm vi dùng chung cần leader review: BE SecurityConfig thêm matcher riêng hai namespace, ArchitectureTest đăng ký Grammar; application.yaml cuối khớp origin/dev. FE nhánh hiện tại còn khác dev ở LearningTopBar, ManagementSidebar, ManagementTopBar và managementNav do tích hợp menu/quản trị/responsive từ các đợt trước. Không nên mô tả toàn bộ nhánh là chỉ sửa file trong hai feature. Auth nền tảng/User/shared backend không có diff với origin/dev trong lần đối chiếu này.

Dữ liệu nghiệp vụ vẫn ở Mongo Docker local, file seed/metadata nằm trong các commit local; riêng96 WAV đã ở Cloudinary. Merge code không tự sao chép database hoặc kết quả/tiến độ cá nhân. Thành viên phải nhận bản code/seed mới, nhập đúng gói vào DB của họ, cấu hình .env và xuất bản nội dung cần demo. Bản tích hợp mới chưa push nên chưa thể khẳng định các thành viên đã nhận được.
