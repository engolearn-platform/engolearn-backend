# Files changed: Placement / Grammar dev integration

These paths are the integration changes after local merge checkpoints BE18e7757/FEcc7ab93. Earlier Grammar authoring/content work was preserved in BE32c72f3/FE3bb7fba. New team code from origin/dev belongs to the merge commits, not a rewrite of other owners.

## engolearn-backend

- `docs/architecture/cross-feature-communication.md`
- `docs/architecture/module-map.md`
- `docs/architecture/runtime-configuration.md`
- `docs/superpowers/plans/2026-11-23-placement-grammar-dev-integration.md`
- `docs/technical/grammar-v2-runtime.md`
- `docs/technical/media/integration-guide.md`
- `docs/technical/placement-grammar-dev-integration.md`
- `docs/technical/placement-local-runtime.md`
- `docs/technical/placement-profile-level-integration.md`
- `src/main/java/fit/iuh/engolearn/config/SecurityConfig.java`
- `src/main/java/fit/iuh/engolearn/grammar/service/impl/GrammarAdminServiceImpl.java`
- `src/main/java/fit/iuh/engolearn/grammar/service/impl/GrammarLearnerServiceImpl.java`
- `src/main/java/fit/iuh/engolearn/placement/controller/PlacementAudioController.java`
- `src/main/java/fit/iuh/engolearn/placement/controller/PlacementLearningController.java`
- `src/main/java/fit/iuh/engolearn/placement/controller/exceptionhandler/PlacementAudioImportExceptionHandler.java`
- `src/main/java/fit/iuh/engolearn/placement/controller/exceptionhandler/PlacementMediaExceptionHandler.java`
- `src/main/java/fit/iuh/engolearn/placement/dto/response/PlacementAdminQuestionMediaResponse.java`
- `src/main/java/fit/iuh/engolearn/placement/dto/response/PlacementAudioMigrationResponse.java`
- `src/main/java/fit/iuh/engolearn/placement/infra/PlacementWavMedia.java`
- `src/main/java/fit/iuh/engolearn/placement/mapper/PlacementSnapshotMapper.java`
- `src/main/java/fit/iuh/engolearn/placement/model/PlacementAudioAsset.java`
- `src/main/java/fit/iuh/engolearn/placement/model/PlacementQuestion.java`
- `src/main/java/fit/iuh/engolearn/placement/model/enums/PlacementAudioAssetStatus.java`
- `src/main/java/fit/iuh/engolearn/placement/model/vo/PlacementAudioVo.java`
- `src/main/java/fit/iuh/engolearn/placement/repository/MongoPlacementQuestionRepository.java`
- `src/main/java/fit/iuh/engolearn/placement/repository/PlacementAudioAssetRepository.java`
- `src/main/java/fit/iuh/engolearn/placement/repository/PlacementAudioMigrationRepository.java`
- `src/main/java/fit/iuh/engolearn/placement/repository/PlacementCopies.java`
- `src/main/java/fit/iuh/engolearn/placement/service/PlacementAudioDelivery.java`
- `src/main/java/fit/iuh/engolearn/placement/service/PlacementAudioImportService.java`
- `src/main/java/fit/iuh/engolearn/placement/service/PlacementDevelopmentAudio.java`
- `src/main/java/fit/iuh/engolearn/placement/service/PlacementTestSetSnapshotFactory.java`
- `src/main/java/fit/iuh/engolearn/placement/service/impl/PlacementAdminAttemptServiceImpl.java`
- `src/main/java/fit/iuh/engolearn/placement/service/impl/PlacementAdminQuestionServiceImpl.java`
- `src/main/java/fit/iuh/engolearn/placement/service/impl/PlacementAdminTestSetServiceImpl.java`
- `src/main/java/fit/iuh/engolearn/placement/service/impl/PlacementAttemptServiceImpl.java`
- `src/main/java/fit/iuh/engolearn/placement/service/impl/PlacementAudioDeliveryImpl.java`
- `src/main/java/fit/iuh/engolearn/placement/service/impl/PlacementAudioImportServiceImpl.java`
- `src/main/java/fit/iuh/engolearn/placement/service/impl/PlacementLearningServiceImpl.java`
- `src/main/java/fit/iuh/engolearn/placement/service/impl/PlacementResultServiceImpl.java`
- `src/main/java/fit/iuh/engolearn/placement/service/impl/PlacementStoredQuestionMediaImpl.java`
- `src/main/resources/application.yaml`
- `src/test/java/fit/iuh/engolearn/placement/controller/PlacementAttemptControllerTest.java`
- `src/test/java/fit/iuh/engolearn/placement/controller/PlacementClaimControllerTest.java`
- `src/test/java/fit/iuh/engolearn/placement/controller/PlacementResultControllerTest.java`
- `src/test/java/fit/iuh/engolearn/placement/infra/PlacementMediaWebConfigTest.java`
- `src/test/java/fit/iuh/engolearn/placement/repository/PlacementCrossIdentityMongoTest.java`
- `src/test/java/fit/iuh/engolearn/placement/repository/PlacementMongoPersistenceTest.java`
- `src/test/java/fit/iuh/engolearn/placement/service/PlacementAdminAttemptServiceTest.java`
- `src/test/java/fit/iuh/engolearn/placement/service/PlacementAttemptServiceTest.java`
- `src/test/java/fit/iuh/engolearn/placement/service/PlacementClaimServiceTest.java`
- `src/test/java/fit/iuh/engolearn/placement/service/PlacementResultServiceTest.java`
- `src/test/java/fit/iuh/engolearn/placement/service/PlacementUserLevelIntegrationTest.java`
- `tools/mongo-json-importer/samples/placement-media-cloudinary.importer.json`
- `tools/placement/import-media.ps1`

## engolearn-frontend

- `docs/features/grammar/00-grammar-learning.md`
- `docs/features/grammar/04-team-dev-integration.md`
- `docs/features/grammar/management/README.md`
- `docs/features/placement/management/README.md`
- `docs/superpowers/plans/2026-11-23-placement-grammar-dev-integration.md`
- `src/features/grammar/admin.routes.ts`
- `src/features/grammar/components/GrammarLearnerGuard.tsx`
- `src/features/grammar/components/admin/GrammarAdminGuard.tsx`
- `src/features/placement/components/learning/attempt/PlacementAudioPlayer.tsx`
- `src/features/placement/components/management/PlacementManagementGuard.tsx`
- `src/features/placement/components/management/questions/PlacementQuestionFields.tsx`
- `src/features/placement/services/placement-management.service.ts`
- `src/features/placement/types/placement-management.types.ts`
- `tests/placement-upload.integration.test.mjs`

Ignored local .env was changed privately (Cloudinary, exact Mongo primary connection and local Speaking off); it is not included in Git. Private DB backups, test accounts and verification credentials remain outside the repo and are not part of seed data. No code changes to Topic/User/Subscription/Speaking beyond incoming dev merge. SecurityConfig is the shared matcher seam, documented in the handoff.
