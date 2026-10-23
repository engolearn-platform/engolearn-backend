package fit.iuh.engolearn.topic.controller;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.shared.web.GlobalExceptionHandler;
import fit.iuh.engolearn.topic.model.Topic;
import fit.iuh.engolearn.topic.model.enums.TopicCategory;
import fit.iuh.engolearn.topic.repository.TopicItemRepository;
import fit.iuh.engolearn.topic.repository.TopicItemStats;
import fit.iuh.engolearn.topic.repository.TopicManagementSearchCriteria;
import fit.iuh.engolearn.topic.repository.TopicRepository;
import fit.iuh.engolearn.topic.service.TopicManagementService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T11 — web-layer tests for the frozen F05 contract (design record §4/§9.1 step 11):
 * HTTP status codes, the {@code Location} header, success payload shape, the
 * error envelope, and validation payloads (Bean Validation, explicit-null PATCH
 * names, unknown-field rejection, malformed JSON, unparseable query params).
 *
 * <p>The real {@link TopicManagementService} is imported (with mocked
 * repositories) so the service-owned PATCH matrix and query-DTO validation are
 * exercised through HTTP exactly as in production; {@link GlobalExceptionHandler}
 * is picked up by the slice as a {@code @RestControllerAdvice}.</p>
 */
@WebMvcTest(TopicManagementController.class)
@Import(TopicManagementService.class)
class TopicManagementControllerTest {

    private static final Instant CREATED = Instant.parse("2026-10-20T09:15:30.123Z");
    private static final Instant UPDATED = Instant.parse("2026-10-20T10:00:00.000Z");
    private static final String BASE = "/api/management/topics";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TopicRepository topicRepository;

    @MockitoBean
    private TopicItemRepository topicItemRepository;

    // -------------------------------------------------------------------- E1

    @Test
    void create_returns201WithLocationHeaderAndTopicBody() throws Exception {
        when(topicRepository.save(any(Topic.class))).thenAnswer(invocation -> withAuditing(invocation.getArgument(0)));

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titleEn":"Food","titleVi":"Ẩm thực"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", BASE + "/generated-id"))
                .andExpect(jsonPath("$.id").value("generated-id"))
                .andExpect(jsonPath("$.titleEn").value("Food"))
                .andExpect(jsonPath("$.titleVi").value("Ẩm thực"))
                .andExpect(jsonPath("$.status").value("draft"))
                .andExpect(jsonPath("$.itemCount").value(0))
                .andExpect(jsonPath("$.durationMinutes").value(0))
                // contract wire type is `string | null` — optional fields are present and null on a fresh draft
                .andExpect(jsonPath("$.cefr").value(nullValue()))
                .andExpect(jsonPath("$.category").value(nullValue()))
                .andExpect(jsonPath("$.description").value(nullValue()))
                .andExpect(jsonPath("$.coverUrl").value(nullValue()))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void create_blankTitleEn_returns400WithFieldErrorEnvelope() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titleEn":"   ","titleVi":"Ẩm thực"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("titleEn"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("titleEn must not be blank"));

        verify(topicRepository, never()).save(any(Topic.class));
    }

    @Test
    void create_missingBothNames_returns400WithBothFieldErrors() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[*].field", containsInAnyOrder("titleEn", "titleVi")));
    }

    @Test
    void create_cefrOutsideRange_returns400() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titleEn":"Food","titleVi":"Ẩm thực","cefr":"C1"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("cefr"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("cefr must be one of A1, A2, B1, B2"));
    }

    @Test
    void create_unknownProperty_returns400WithoutFieldErrors() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titleEn":"Food","titleVi":"Ẩm thực","unexpected":1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void create_malformedJson_returns400Envelope() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titleEn\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    // -------------------------------------------------------------------- E2

    @Test
    void get_returns200WithDerivedStatusAndComputedFields() throws Exception {
        when(topicRepository.findById("t1")).thenReturn(Optional.of(topic("t1", 3)));
        when(topicItemRepository.statsByTopicId("t1")).thenReturn(new TopicItemStats(4, 90));

        mockMvc.perform(get(BASE + "/t1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("t1"))
                .andExpect(jsonPath("$.status").value("published"))
                .andExpect(jsonPath("$.itemCount").value(4))
                .andExpect(jsonPath("$.durationMinutes").value(2))
                .andExpect(jsonPath("$.cefr").value("B1"))
                .andExpect(jsonPath("$.category").value("DINING"));
    }

    @Test
    void get_unknownId_returns404WithEnvelopeAndNoFieldErrors() throws Exception {
        when(topicRepository.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get(BASE + "/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Topic not found: missing"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    // -------------------------------------------------------------------- E3

    @Test
    void patch_absentFieldsAreKept_returns200() throws Exception {
        when(topicRepository.findById("t1")).thenReturn(Optional.of(topic("t1", null)));
        when(topicRepository.save(any(Topic.class))).thenAnswer(invocation -> withAuditing(invocation.getArgument(0)));
        when(topicItemRepository.statsByTopicId("t1")).thenReturn(TopicItemStats.EMPTY);

        mockMvc.perform(patch(BASE + "/t1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"new description"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("t1"))
                .andExpect(jsonPath("$.description").value("new description"))
                .andExpect(jsonPath("$.titleEn").value("Food"))
                .andExpect(jsonPath("$.cefr").value("B1"))
                .andExpect(jsonPath("$.category").value("DINING"))
                .andExpect(jsonPath("$.status").value("draft"));
    }

    @Test
    void patch_explicitNullOptionalFields_clearsThemAndReturns200() throws Exception {
        when(topicRepository.findById("t1")).thenReturn(Optional.of(topic("t1", null)));
        when(topicRepository.save(any(Topic.class))).thenAnswer(invocation -> withAuditing(invocation.getArgument(0)));
        when(topicItemRepository.statsByTopicId("t1")).thenReturn(TopicItemStats.EMPTY);

        mockMvc.perform(patch(BASE + "/t1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cefr":null,"category":null,"description":null,"coverUrl":null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cefr").value(nullValue()))
                .andExpect(jsonPath("$.category").value(nullValue()))
                .andExpect(jsonPath("$.description").value(nullValue()))
                .andExpect(jsonPath("$.coverUrl").value(nullValue()))
                .andExpect(jsonPath("$.titleEn").value("Food"));
    }

    @Test
    void patch_explicitNullTitleEn_returns400BecauseNamesCanNeverBeCleared() throws Exception {
        when(topicRepository.findById("t1")).thenReturn(Optional.of(topic("t1", null)));

        mockMvc.perform(patch(BASE + "/t1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titleEn":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("titleEn"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("titleEn must not be blank"));

        verify(topicRepository, never()).save(any(Topic.class));
    }

    @Test
    void patch_blankTitleVi_returns400() throws Exception {
        when(topicRepository.findById("t1")).thenReturn(Optional.of(topic("t1", null)));

        mockMvc.perform(patch(BASE + "/t1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titleVi":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("titleVi"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("titleVi must not be blank"));
    }

    @Test
    void patch_unknownField_returns400() throws Exception {
        mockMvc.perform(patch(BASE + "/t1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"unknown":"value"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());

        verify(topicRepository, never()).save(any(Topic.class));
    }

    @Test
    void patch_nonTextualTitle_returns400() throws Exception {
        mockMvc.perform(patch(BASE + "/t1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titleEn":123}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void patch_cefrOutsideRange_returns400() throws Exception {
        when(topicRepository.findById("t1")).thenReturn(Optional.of(topic("t1", null)));

        mockMvc.perform(patch(BASE + "/t1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cefr":"C1"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("cefr"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("cefr must be one of A1, A2, B1, B2"));
    }

    @Test
    void patch_emptyBody_returns200WithUnchangedTopic() throws Exception {
        when(topicRepository.findById("t1")).thenReturn(Optional.of(topic("t1", null)));
        when(topicRepository.save(any(Topic.class))).thenAnswer(invocation -> withAuditing(invocation.getArgument(0)));
        when(topicItemRepository.statsByTopicId("t1")).thenReturn(TopicItemStats.EMPTY);

        mockMvc.perform(patch(BASE + "/t1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titleEn").value("Food"))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void patch_unknownId_returns404() throws Exception {
        when(topicRepository.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(patch(BASE + "/missing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"x"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Topic not found: missing"));
    }

    // -------------------------------------------------------------------- E4

    @Test
    void list_returns200WithItemsCountersAndPaging() throws Exception {
        when(topicRepository.search(any(TopicManagementSearchCriteria.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(topic("t1", 3)), PageRequest.of(0, 20), 1));
        when(topicItemRepository.statsByTopicIds(any())).thenReturn(Map.of("t1", new TopicItemStats(2, 30)));
        when(topicRepository.count()).thenReturn(7L);
        when(topicRepository.countByLatestPublishedVersionNotNull()).thenReturn(3L);

        mockMvc.perform(get(BASE).param("search", "food").param("cefr", "B1")
                        .param("category", "DINING").param("status", "published"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("t1"))
                .andExpect(jsonPath("$.items[0].titleEn").value("Food"))
                .andExpect(jsonPath("$.items[0].status").value("published"))
                .andExpect(jsonPath("$.items[0].itemCount").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.counters.total").value(7))
                .andExpect(jsonPath("$.counters.published").value(3))
                .andExpect(jsonPath("$.counters.draft").value(4));
    }

    @Test
    void list_withoutParams_appliesDefaultPageAndSizeAndNoFilters() throws Exception {
        when(topicRepository.search(any(TopicManagementSearchCriteria.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        when(topicItemRepository.statsByTopicIds(any())).thenReturn(Map.of());
        when(topicRepository.count()).thenReturn(0L);
        when(topicRepository.countByLatestPublishedVersionNotNull()).thenReturn(0L);

        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.counters.total").value(0));

        ArgumentCaptor<TopicManagementSearchCriteria> criteria = ArgumentCaptor.forClass(TopicManagementSearchCriteria.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(topicRepository).search(criteria.capture(), pageable.capture());

        assertThat(criteria.getValue().search()).isNull();
        assertThat(criteria.getValue().cefr()).isNull();
        assertThat(criteria.getValue().category()).isNull();
        assertThat(criteria.getValue().published()).isNull();
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void list_emptyResult_returns200WithEmptyItems() throws Exception {
        when(topicRepository.search(any(TopicManagementSearchCriteria.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        when(topicItemRepository.statsByTopicIds(any())).thenReturn(Map.of());
        when(topicRepository.count()).thenReturn(0L);
        when(topicRepository.countByLatestPublishedVersionNotNull()).thenReturn(0L);

        mockMvc.perform(get(BASE).param("search", "nothing-matches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"archived", "BETA", "DRAFT"})
    void list_invalidStatus_returns400(String status) throws Exception {
        mockMvc.perform(get(BASE).param("status", status))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("status"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("status must be one of draft, published"));
    }

    @Test
    void list_invalidCefr_returns400() throws Exception {
        mockMvc.perform(get(BASE).param("cefr", "C1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("cefr"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "101"})
    void list_sizeOutOfRange_returns400(String size) throws Exception {
        mockMvc.perform(get(BASE).param("size", size))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("size"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("size must be between 1 and 100"));
    }

    @Test
    void list_negativePage_returns400() throws Exception {
        mockMvc.perform(get(BASE).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("page"));
    }

    @Test
    void list_nonNumericPage_returns400WithParamFieldError() throws Exception {
        mockMvc.perform(get(BASE).param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("page"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Invalid value 'abc' for parameter 'page'"));
    }

    @Test
    void list_overlongSearch_returns400() throws Exception {
        mockMvc.perform(get(BASE).param("search", "x".repeat(201)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("search"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("search must be at most 200 characters"));
    }

    // --------------------------------------------------------------- helpers

    private static Topic topic(String id, Integer latestPublishedVersion) {
        return Topic.builder()
                .id(id)
                .titleEn("Food")
                .titleVi("Ẩm thực")
                .cefrLevel(CefrLevel.B1)
                .category(TopicCategory.DINING)
                .latestPublishedVersion(latestPublishedVersion)
                .createdAt(CREATED)
                .updatedAt(UPDATED)
                .build();
    }

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
