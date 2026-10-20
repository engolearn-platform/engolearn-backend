package fit.iuh.engolearn.topic.controller;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.topic.dto.response.TopicDetailResponse;
import fit.iuh.engolearn.topic.dto.response.TopicItemSummaryResponse;
import fit.iuh.engolearn.topic.dto.response.TopicPageResponse;
import fit.iuh.engolearn.topic.dto.response.TopicSummaryResponse;
import fit.iuh.engolearn.topic.exception.InvalidBrowseRequestException;
import fit.iuh.engolearn.topic.exception.TopicNotFoundException;
import fit.iuh.engolearn.topic.service.TopicBrowseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests of the topic browse endpoints with a mocked service.
 *
 * <p>What these tests prove: the routing of both endpoints, the JSON payload the frontend sees
 * (field names, absent fields, cursor flags), and the HTTP status of each rejection. What they
 * do not prove: the browse rules themselves, which are covered by the service unit tests, and
 * the real Mongo query.
 */
@WebMvcTest(TopicBrowseController.class)
class TopicBrowseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TopicBrowseService topicBrowseService;

    // --- list ---------------------------------------------------------------------------

    @Test
    void listReturnsTheCardNumbersAndNeverExposesTheTopicDescription() throws Exception {
        when(topicBrowseService.getAvailableTopics(any())).thenReturn(TopicPageResponse.builder()
                .items(List.of(summary("t1")))
                .nextCursor(null)
                .hasMore(false)
                .build());

        mockMvc.perform(get("/api/v1/topics")
                        .param("level", "A1")
                        .param("keyword", "airport")
                        .param("sort", "recommended"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].topicId").value("t1"))
                .andExpect(jsonPath("$.data.items[0].cefrLevel").value("A1"))
                .andExpect(jsonPath("$.data.items[0].vocabularyCount").value(3))
                .andExpect(jsonPath("$.data.items[0].quizCount").value(2))
                .andExpect(jsonPath("$.data.items[0].durationMinutes").value(15))
                // A card never carries the topic description.
                .andExpect(jsonPath("$.data.items[0].description").doesNotExist())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void listExposesTheNextCursorOnlyWhileMoreTopicsRemain() throws Exception {
        when(topicBrowseService.getAvailableTopics(any())).thenReturn(TopicPageResponse.builder()
                .items(List.of(summary("t1")))
                .nextCursor("opaque-next-cursor")
                .hasMore(true)
                .build());

        mockMvc.perform(get("/api/v1/topics").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasMore").value(true))
                .andExpect(jsonPath("$.data.nextCursor").value("opaque-next-cursor"));
    }

    @Test
    void listEndsWithoutACursorWhenThereIsNoFurtherPage() throws Exception {
        when(topicBrowseService.getAvailableTopics(any())).thenReturn(TopicPageResponse.builder()
                .items(List.of(summary("t1")))
                .nextCursor(null)
                .hasMore(false)
                .build());

        mockMvc.perform(get("/api/v1/topics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasMore").value(false))
                .andExpect(jsonPath("$.data.nextCursor").value(nullValue()));
    }

    // --- detail -------------------------------------------------------------------------

    @Test
    void detailReturnsTheItemsInTheOrderTheServiceReturnedThem() throws Exception {
        when(topicBrowseService.getTopicDetail(any())).thenReturn(TopicDetailResponse.builder()
                .topicId("t1")
                .titleEn("At the airport")
                .titleVi("O san bay")
                .cefrLevel(CefrLevel.A2)
                .description("Airport situations")
                .items(List.of(
                        item("i3", "Check-in", 20),
                        item("i1", "Boarding", 10),
                        item("i2", "Arrival", 5)))
                .build());

        mockMvc.perform(get("/api/v1/topics/t1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.topicId").value("t1"))
                .andExpect(jsonPath("$.data.titleEn").value("At the airport"))
                .andExpect(jsonPath("$.data.titleVi").value("O san bay"))
                .andExpect(jsonPath("$.data.cefrLevel").value("A2"))
                .andExpect(jsonPath("$.data.description").value("Airport situations"))
                .andExpect(jsonPath("$.data.items[*].topicItemId", contains("i3", "i1", "i2")))
                .andExpect(jsonPath("$.data.items[*].title", contains("Check-in", "Boarding", "Arrival")))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // --- error mapping ------------------------------------------------------------------

    @Test
    void anUnknownTopicIsReportedAsNotFound() throws Exception {
        when(topicBrowseService.getTopicDetail(any()))
                .thenThrow(new TopicNotFoundException("Topic nope was not found"));

        mockMvc.perform(get("/api/v1/topics/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Topic nope was not found"));
    }

    @Test
    void anUnknownLevelIsReportedAsABadRequest() throws Exception {
        when(topicBrowseService.getAvailableTopics(any()))
                .thenThrow(new InvalidBrowseRequestException("level is not a CEFR level: C9"));

        mockMvc.perform(get("/api/v1/topics").param("level", "C9"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void anOutOfRangeLimitIsReportedAsABadRequest() throws Exception {
        when(topicBrowseService.getAvailableTopics(any()))
                .thenThrow(new InvalidBrowseRequestException("limit must be between 1 and 100"));

        mockMvc.perform(get("/api/v1/topics").param("limit", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void aNonNumericLimitIsReportedAsABadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/topics").param("limit", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // The value never reaches the service: the conversion fails before the call.
        verifyNoInteractions(topicBrowseService);
    }

    // --- camelCase payload --------------------------------------------------------------

    @Test
    void thePayloadUsesCamelCaseFieldNames() throws Exception {
        when(topicBrowseService.getAvailableTopics(any())).thenReturn(TopicPageResponse.builder()
                .items(List.of(summary("t1")))
                .nextCursor("c")
                .hasMore(true)
                .build());
        when(topicBrowseService.getTopicDetail(any())).thenReturn(TopicDetailResponse.builder()
                .topicId("t1")
                .titleEn("At the airport")
                .titleVi("O san bay")
                .cefrLevel(CefrLevel.A1)
                .description("Airport situations")
                .items(List.of(item("i1", "Boarding", 10)))
                .build());

        mockMvc.perform(get("/api/v1/topics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.data.nextCursor").exists())
                .andExpect(jsonPath("$.data.hasMore").exists())
                .andExpect(jsonPath("$.data.items[0].topicId").exists())
                .andExpect(jsonPath("$.data.items[0].coverUrl").exists())
                .andExpect(jsonPath("$.data.items[0].titleEn").exists())
                .andExpect(jsonPath("$.data.items[0].titleVi").exists())
                .andExpect(jsonPath("$.data.items[0].cefrLevel").exists())
                .andExpect(jsonPath("$.data.items[0].vocabularyCount").exists())
                .andExpect(jsonPath("$.data.items[0].quizCount").exists())
                .andExpect(jsonPath("$.data.items[0].durationMinutes").exists());

        mockMvc.perform(get("/api/v1/topics/t1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.topicId").exists())
                .andExpect(jsonPath("$.data.items[0].topicItemId").exists())
                .andExpect(jsonPath("$.data.items[0].durationMinutes").exists())
                .andExpect(jsonPath("$.data.items[0].vocabularyCount").exists())
                .andExpect(jsonPath("$.data.items[0].expressionCount").exists())
                .andExpect(jsonPath("$.data.items[0].quizCount").exists());
    }

    // --- helpers ------------------------------------------------------------------------

    private static TopicSummaryResponse summary(String id) {
        return TopicSummaryResponse.builder()
                .topicId(id)
                .coverUrl("https://cdn.example.test/" + id + ".jpg")
                .titleEn("Title " + id)
                .titleVi("Tieu de " + id)
                .cefrLevel(CefrLevel.A1)
                .vocabularyCount(3)
                .quizCount(2)
                .durationMinutes(15)
                .build();
    }

    private static TopicItemSummaryResponse item(String topicItemId, String title, int durationMinutes) {
        return TopicItemSummaryResponse.builder()
                .topicItemId(topicItemId)
                .title(title)
                .description("Short description of " + title)
                .durationMinutes(durationMinutes)
                .vocabularyCount(4)
                .expressionCount(1)
                .quizCount(2)
                .build();
    }
}
