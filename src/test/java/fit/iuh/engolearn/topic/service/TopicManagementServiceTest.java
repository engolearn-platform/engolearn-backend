package fit.iuh.engolearn.topic.service;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.shared.exception.ResourceNotFoundException;
import fit.iuh.engolearn.shared.exception.ValidationFailedException;
import fit.iuh.engolearn.shared.web.ApiFieldError;
import fit.iuh.engolearn.topic.dto.request.TopicCreateRequest;
import fit.iuh.engolearn.topic.dto.request.TopicManagementListQuery;
import fit.iuh.engolearn.topic.dto.request.TopicUpdateRequest;
import fit.iuh.engolearn.topic.dto.response.TopicManagementListResponse;
import fit.iuh.engolearn.topic.dto.response.TopicManagementResponse;
import fit.iuh.engolearn.topic.model.Topic;
import fit.iuh.engolearn.topic.model.enums.TopicCategory;
import fit.iuh.engolearn.topic.repository.TopicItemStats;
import fit.iuh.engolearn.topic.repository.TopicItemVersionRepository;
import fit.iuh.engolearn.topic.repository.TopicManagementSearchCriteria;
import fit.iuh.engolearn.topic.repository.TopicRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * T9 — service unit tests for the frozen F05 semantics (design §9.1 step 8):
 * minimal create, blank-name 400s, CEFR range, closed category set, the PATCH
 * absent/null/blank matrix, published-topic in-place edit (no version writes),
 * latest-version duration derivation and counters-over-unfiltered-set.
 */
@ExtendWith(MockitoExtension.class)
class TopicManagementServiceTest {

    private static final Instant CREATED = Instant.parse("2026-10-20T09:15:30.123Z");
    private static final Instant UPDATED = Instant.parse("2026-10-20T10:00:00.000Z");

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private TopicItemVersionRepository topicItemVersionRepository;

    private TopicManagementService service;

    @BeforeEach
    void setUp() {
        service = new TopicManagementService(topicRepository, topicItemVersionRepository, VALIDATOR);
    }

    // ---------------------------------------------------------------- create

    @Test
    void create_withNamesOnly_storesNothingElseAndReturnsDraftWithZeroComputedValues() {
        when(topicRepository.save(any(Topic.class))).thenAnswer(invocation -> withAuditing(invocation.getArgument(0)));

        TopicManagementResponse response = service.create(new TopicCreateRequest(
                "  Ordering Food  ", "  Gọi món  ", null, null, null, null));

        assertThat(response.id()).isEqualTo("generated-id");
        assertThat(response.titleEn()).isEqualTo("Ordering Food");
        assertThat(response.titleVi()).isEqualTo("Gọi món");
        assertThat(response.cefr()).isNull();
        assertThat(response.category()).isNull();
        assertThat(response.description()).isNull();
        assertThat(response.coverUrl()).isNull();
        assertThat(response.status()).isEqualTo("draft");
        assertThat(response.durationMinutes()).isZero();
        assertThat(response.itemCount()).isZero();
        assertThat(response.createdAt()).isEqualTo(CREATED);

        ArgumentCaptor<Topic> saved = ArgumentCaptor.forClass(Topic.class);
        verify(topicRepository).save(saved.capture());
        Topic persisted = saved.getValue();
        assertThat(persisted.getLatestPublishedVersion()).isNull();
        assertThat(persisted.getLatestBetaVersion()).isNull();
        assertThat(persisted.getDurationMinutes()).isNull();
        assertThat(persisted.getTags()).isNull();
        assertThat(persisted.getOrderIndex()).isNull();
        verifyNoInteractions(topicItemVersionRepository);
    }

    @Test
    void create_withAllOptionalFields_storesEnumBackedValues() {
        when(topicRepository.save(any(Topic.class))).thenAnswer(invocation -> withAuditing(invocation.getArgument(0)));

        TopicManagementResponse response = service.create(new TopicCreateRequest(
                "Food", "Ẩm thực", "B1", "DINING", "desc", "https://img/cover.png"));

        assertThat(response.cefr()).isEqualTo(CefrLevel.B1);
        assertThat(response.category()).isEqualTo(TopicCategory.DINING);
        assertThat(response.description()).isEqualTo("desc");
        assertThat(response.coverUrl()).isEqualTo("https://img/cover.png");
    }

    @Test
    void create_blankTitleEn_isRejectedWithFieldError() {
        assertFieldError(() -> service.create(new TopicCreateRequest("   ", "Gọi món", null, null, null, null)),
                "titleEn", "titleEn must not be blank");
        verify(topicRepository, never()).save(any(Topic.class));
    }

    @Test
    void create_missingTitleVi_isRejectedWithFieldError() {
        assertFieldError(() -> service.create(new TopicCreateRequest("Food", null, null, null, null, null)),
                "titleVi", "titleVi must not be blank");
    }

    @ParameterizedTest
    @ValueSource(strings = {"C1", "C2", "A0", "B3", "a1"})
    void create_cefrOutsideAssignableRange_isRejected(String cefr) {
        assertFieldError(() -> service.create(new TopicCreateRequest("Food", "Ẩm thực", cefr, null, null, null)),
                "cefr", "cefr must be one of A1, A2, B1, B2");
    }

    @Test
    void create_categoryOutsideClosedSet_isRejected() {
        assertFieldError(() -> service.create(new TopicCreateRequest("Food", "Ẩm thực", null, "FOOD", null, null)),
                "category", "category must be one of DINING, TRAVEL, BUSINESS, DAILY, HEALTH");
    }

    @Test
    void create_blankCoverUrlWhenPresent_isRejected() {
        assertFieldError(() -> service.create(new TopicCreateRequest("Food", "Ẩm thực", null, null, null, "   ")),
                "coverUrl", "coverUrl must not be blank when present");
    }

    @Test
    void create_overlongTitle_isRejected() {
        String tooLong = "x".repeat(201);
        assertFieldError(() -> service.create(new TopicCreateRequest(tooLong, "Ẩm thực", null, null, null, null)),
                "titleEn", "titleEn must be at most 200 characters");
    }

    // ------------------------------------------------------------------- get

    @Test
    void get_returnsComputedFieldsAndDerivedStatus() {
        Topic topic = topic("t1", "Food", "Ẩm thực", 3);
        when(topicRepository.findById("t1")).thenReturn(Optional.of(topic));
        when(topicItemVersionRepository.statsByTopicId("t1")).thenReturn(new TopicItemStats(4, 90));

        TopicManagementResponse response = service.get("t1");

        assertThat(response.id()).isEqualTo("t1");
        assertThat(response.status()).isEqualTo("published");
        assertThat(response.itemCount()).isEqualTo(4);
        assertThat(response.durationMinutes()).isEqualTo(90);
    }

    @ParameterizedTest
    @CsvSource({"0,0", "1,1", "59,59", "60,60", "61,61", "119,119", "120,120", "121,121"})
    void get_durationMinutes_sumsStoredMinutesWithoutRounding(long totalDurationMinutes, int expectedMinutes) {
        when(topicRepository.findById("t1")).thenReturn(Optional.of(topic("t1", "Food", "Ẩm thực", null)));
        when(topicItemVersionRepository.statsByTopicId("t1")).thenReturn(new TopicItemStats(1, totalDurationMinutes));

        assertThat(service.get("t1").durationMinutes()).isEqualTo(expectedMinutes);
    }

    @Test
    void get_topicWithOnlyBetaVersion_isStillDraft() {
        Topic topic = topic("t1", "Food", "Ẩm thực", null);
        topic.setLatestBetaVersion(5);
        when(topicRepository.findById("t1")).thenReturn(Optional.of(topic));
        when(topicItemVersionRepository.statsByTopicId("t1")).thenReturn(TopicItemStats.EMPTY);

        assertThat(service.get("t1").status()).isEqualTo("draft");
    }

    @Test
    void get_unknownId_throwsResourceNotFoundWithContractMessage() {
        when(topicRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("missing"))
                .isInstanceOfSatisfying(ResourceNotFoundException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("RESOURCE_NOT_FOUND");
                    assertThat(exception.getMessage()).isEqualTo("Topic not found: missing");
                    assertThat(exception.getStatusCode().value()).isEqualTo(404);
                });
    }

    // ---------------------------------------------------------------- update

    @Test
    void update_absentFields_areKept() {
        Topic existing = fullDraft("t1");
        when(topicRepository.findById("t1")).thenReturn(Optional.of(existing));
        when(topicRepository.save(any(Topic.class))).thenAnswer(invocation -> withAuditing(invocation.getArgument(0)));
        when(topicItemVersionRepository.statsByTopicId("t1")).thenReturn(TopicItemStats.EMPTY);

        TopicManagementResponse response = service.update("t1",
                updateRequest(Set.of(TopicUpdateRequest.DESCRIPTION), null, null, null, null, "new description", null));

        assertThat(response.id()).isEqualTo("t1");
        assertThat(response.titleEn()).isEqualTo("Food");
        assertThat(response.titleVi()).isEqualTo("Ẩm thực");
        assertThat(response.cefr()).isEqualTo(CefrLevel.B1);
        assertThat(response.category()).isEqualTo(TopicCategory.DINING);
        assertThat(response.coverUrl()).isEqualTo("https://img/cover.png");
        assertThat(response.description()).isEqualTo("new description");
        assertThat(response.status()).isEqualTo("draft");
    }

    @Test
    void update_explicitNull_clearsOptionalFields() {
        Topic existing = fullDraft("t1");
        when(topicRepository.findById("t1")).thenReturn(Optional.of(existing));
        when(topicRepository.save(any(Topic.class))).thenAnswer(invocation -> withAuditing(invocation.getArgument(0)));
        when(topicItemVersionRepository.statsByTopicId("t1")).thenReturn(TopicItemStats.EMPTY);

        TopicManagementResponse response = service.update("t1", updateRequest(
                Set.of(TopicUpdateRequest.CEFR, TopicUpdateRequest.CATEGORY,
                        TopicUpdateRequest.DESCRIPTION, TopicUpdateRequest.COVER_URL),
                null, null, null, null, null, null));

        assertThat(response.cefr()).isNull();
        assertThat(response.category()).isNull();
        assertThat(response.description()).isNull();
        assertThat(response.coverUrl()).isNull();
        assertThat(response.titleEn()).isEqualTo("Food");
        assertThat(response.titleVi()).isEqualTo("Ẩm thực");
    }

    @Test
    void update_explicitNullTitleEn_isRejected() {
        when(topicRepository.findById("t1")).thenReturn(Optional.of(fullDraft("t1")));

        assertFieldError(() -> service.update("t1",
                        updateRequest(Set.of(TopicUpdateRequest.TITLE_EN), null, null, null, null, null, null)),
                "titleEn", "titleEn must not be blank");
        verify(topicRepository, never()).save(any(Topic.class));
    }

    @Test
    void update_blankTitleVi_isRejected() {
        when(topicRepository.findById("t1")).thenReturn(Optional.of(fullDraft("t1")));

        assertFieldError(() -> service.update("t1",
                        updateRequest(Set.of(TopicUpdateRequest.TITLE_VI), null, "   ", null, null, null, null)),
                "titleVi", "titleVi must not be blank");
    }

    @Test
    void update_trimsProvidedTitles() {
        when(topicRepository.findById("t1")).thenReturn(Optional.of(fullDraft("t1")));
        when(topicRepository.save(any(Topic.class))).thenAnswer(invocation -> withAuditing(invocation.getArgument(0)));
        when(topicItemVersionRepository.statsByTopicId("t1")).thenReturn(TopicItemStats.EMPTY);

        TopicManagementResponse response = service.update("t1", updateRequest(
                Set.of(TopicUpdateRequest.TITLE_EN, TopicUpdateRequest.TITLE_VI),
                "  New EN  ", "  New VI  ", null, null, null, null));

        assertThat(response.titleEn()).isEqualTo("New EN");
        assertThat(response.titleVi()).isEqualTo("New VI");
    }

    @Test
    void update_invalidCefrOnPatch_isRejected() {
        assertFieldError(() -> service.update("t1",
                        updateRequest(Set.of(TopicUpdateRequest.CEFR), null, null, "C1", null, null, null)),
                "cefr", "cefr must be one of A1, A2, B1, B2");
        verify(topicRepository, never()).save(any(Topic.class));
    }

    @Test
    void update_blankCoverUrlWhenPresent_isRejected() {
        when(topicRepository.findById("t1")).thenReturn(Optional.of(fullDraft("t1")));

        assertFieldError(() -> service.update("t1",
                        updateRequest(Set.of(TopicUpdateRequest.COVER_URL), null, null, null, null, null, "  ")),
                "coverUrl", "coverUrl must not be blank when present");
    }

    @Test
    void update_publishedTopic_editsInPlaceAndKeepsVersionMarkers() {
        Topic published = topic("t1", "Food", "Ẩm thực", 2);
        published.setLatestBetaVersion(1);
        when(topicRepository.findById("t1")).thenReturn(Optional.of(published));
        when(topicRepository.save(any(Topic.class))).thenAnswer(invocation -> withAuditing(invocation.getArgument(0)));
        when(topicItemVersionRepository.statsByTopicId("t1")).thenReturn(TopicItemStats.EMPTY);

        TopicManagementResponse response = service.update("t1",
                updateRequest(Set.of(TopicUpdateRequest.DESCRIPTION), null, null, null, null, "edited", null));

        assertThat(response.id()).isEqualTo("t1");
        assertThat(response.status()).isEqualTo("published");

        ArgumentCaptor<Topic> saved = ArgumentCaptor.forClass(Topic.class);
        verify(topicRepository).save(saved.capture());
        assertThat(saved.getValue().getLatestPublishedVersion()).isEqualTo("2");
        assertThat(saved.getValue().getLatestBetaVersion()).isEqualTo(1);
        assertThat(saved.getValue().getId()).isEqualTo("t1");
    }

    @Test
    void update_emptyBody_stillSavesAndReturnsOk() {
        when(topicRepository.findById("t1")).thenReturn(Optional.of(fullDraft("t1")));
        when(topicRepository.save(any(Topic.class))).thenAnswer(invocation -> withAuditing(invocation.getArgument(0)));
        when(topicItemVersionRepository.statsByTopicId("t1")).thenReturn(TopicItemStats.EMPTY);

        TopicManagementResponse response = service.update("t1",
                updateRequest(Set.of(), null, null, null, null, null, null));

        assertThat(response.titleEn()).isEqualTo("Food");
        assertThat(response.updatedAt()).isEqualTo(UPDATED);
        verify(topicRepository).save(any(Topic.class));
    }

    @Test
    void update_unknownId_throwsResourceNotFound() {
        when(topicRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("missing",
                updateRequest(Set.of(TopicUpdateRequest.DESCRIPTION), null, null, null, null, "x", null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ------------------------------------------------------------------ list

    @Test
    void list_parsesFiltersIntoCriteria_andUsesFixedSort() {
        Topic draft = topic("t1", "Food", "Ẩm thực", null);
        when(topicRepository.search(any(TopicManagementSearchCriteria.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(draft), PageRequest.of(0, 20), 1));
        when(topicItemVersionRepository.statsByTopicIds(any())).thenReturn(Map.of("t1", new TopicItemStats(2, 30)));
        when(topicRepository.count()).thenReturn(7L);
        when(topicRepository.countByLatestPublishedVersionNotNull()).thenReturn(3L);

        TopicManagementListResponse response = service.list(
                new TopicManagementListQuery("food", "B1", "DINING", "published", 0, 20));

        ArgumentCaptor<TopicManagementSearchCriteria> criteria = ArgumentCaptor.forClass(TopicManagementSearchCriteria.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(topicRepository).search(criteria.capture(), pageable.capture());

        assertThat(criteria.getValue().search()).isEqualTo("food");
        assertThat(criteria.getValue().cefr()).isEqualTo(CefrLevel.B1);
        assertThat(criteria.getValue().category()).isEqualTo(TopicCategory.DINING);
        assertThat(criteria.getValue().published()).isTrue();
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "updatedAt"));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().id()).isEqualTo("t1");
        assertThat(response.items().getFirst().status()).isEqualTo("draft");
        assertThat(response.items().getFirst().itemCount()).isEqualTo(2);
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.counters().total()).isEqualTo(7);
        assertThat(response.counters().published()).isEqualTo(3);
        assertThat(response.counters().draft()).isEqualTo(4);
    }

    @Test
    void list_statusDraft_mapsToUnpublishedCriteria() {
        when(topicRepository.search(any(TopicManagementSearchCriteria.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        when(topicItemVersionRepository.statsByTopicIds(any())).thenReturn(Map.of());
        when(topicRepository.count()).thenReturn(0L);
        when(topicRepository.countByLatestPublishedVersionNotNull()).thenReturn(0L);

        TopicManagementListResponse response = service.list(
                new TopicManagementListQuery(null, null, null, "draft", 0, 20));

        ArgumentCaptor<TopicManagementSearchCriteria> criteria = ArgumentCaptor.forClass(TopicManagementSearchCriteria.class);
        verify(topicRepository).search(criteria.capture(), any(Pageable.class));
        assertThat(criteria.getValue().published()).isFalse();
        assertThat(criteria.getValue().search()).isNull();
        assertThat(response.items()).isEmpty();
        assertThat(response.counters().total()).isZero();
        assertThat(response.counters().draft()).isZero();
    }

    @Test
    void list_itemWithoutStats_reportsZeroItemCount() {
        Topic draft = topic("t1", "Food", "Ẩm thực", null);
        when(topicRepository.search(any(TopicManagementSearchCriteria.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(draft), PageRequest.of(0, 20), 1));
        when(topicItemVersionRepository.statsByTopicIds(any())).thenReturn(Map.of());
        when(topicRepository.count()).thenReturn(1L);
        when(topicRepository.countByLatestPublishedVersionNotNull()).thenReturn(0L);

        assertThat(service.list(new TopicManagementListQuery(null, null, null, null, 0, 20))
                .items().getFirst().itemCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, -5})
    void list_negativePage_isRejected(int page) {
        assertFieldError(() -> service.list(new TopicManagementListQuery(null, null, null, null, page, 20)),
                "page", "page must be greater than or equal to 0");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 101, 500})
    void list_sizeOutOfRange_isRejected(int size) {
        assertFieldError(() -> service.list(new TopicManagementListQuery(null, null, null, null, 0, size)),
                "size", "size must be between 1 and 100");
    }

    @ParameterizedTest
    @ValueSource(strings = {"archived", "BETA", "DRAFT", ""})
    void list_invalidStatus_isRejected(String status) {
        assertFieldError(() -> service.list(new TopicManagementListQuery(null, null, null, status, 0, 20)),
                "status", "status must be one of draft, published");
    }

    @Test
    void list_invalidCefrParam_isRejected() {
        assertFieldError(() -> service.list(new TopicManagementListQuery(null, "C1", null, null, 0, 20)),
                "cefr", "cefr must be one of A1, A2, B1, B2");
    }

    @Test
    void list_invalidCategoryParam_isRejected() {
        assertFieldError(() -> service.list(new TopicManagementListQuery(null, null, "dining-food", null, 0, 20)),
                "category", "category must be one of DINING, TRAVEL, BUSINESS, DAILY, HEALTH");
    }

    @Test
    void list_overlongSearch_isRejected() {
        assertFieldError(() -> service.list(new TopicManagementListQuery("x".repeat(201), null, null, null, 0, 20)),
                "search", "search must be at most 200 characters");
    }

    // --------------------------------------------------------------- helpers

    private static void assertFieldError(Runnable action, String field, String message) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ValidationFailedException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("VALIDATION_ERROR");
                    assertThat(exception.getStatusCode().value()).isEqualTo(400);
                    assertThat(exception.getFieldErrors())
                            .extracting(ApiFieldError::field)
                            .containsExactly(field);
                    assertThat(exception.getFieldErrors())
                            .extracting(ApiFieldError::message)
                            .containsExactly(message);
                });
    }

    private static TopicUpdateRequest updateRequest(Set<String> provided,
                                                    String titleEn,
                                                    String titleVi,
                                                    String cefr,
                                                    String category,
                                                    String description,
                                                    String coverUrl) {
        return new TopicUpdateRequest(titleEn, titleVi, cefr, category, description, coverUrl, provided);
    }

    private static Topic topic(String id, String titleEn, String titleVi, Integer latestPublishedVersion) {
        return Topic.builder()
                .id(id)
                .titleEn(titleEn)
                .titleVi(titleVi)
                .latestPublishedVersion(latestPublishedVersion == null ? null : latestPublishedVersion.toString())
                .createdAt(CREATED)
                .updatedAt(UPDATED)
                .build();
    }

    private static Topic fullDraft(String id) {
        return Topic.builder()
                .id(id)
                .titleEn("Food")
                .titleVi("Ẩm thực")
                .cefrLevel(CefrLevel.B1)
                .category(TopicCategory.DINING)
                .description("old description")
                .coverUrl("https://img/cover.png")
                .createdAt(CREATED)
                .updatedAt(UPDATED)
                .build();
    }

    /** Mimics the repository/auditing contract: assigns an id and timestamps. */
    private static Topic withAuditing(Topic topic) {
        if (topic.getId() == null) {
            topic.setId("generated-id");
        }
        if (topic.getCreatedAt() == null) {
            topic.setCreatedAt(CREATED);
        }
        topic.setUpdatedAt(UPDATED);
        return topic;
    }
}
