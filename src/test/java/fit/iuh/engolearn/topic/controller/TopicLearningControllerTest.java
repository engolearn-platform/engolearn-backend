package fit.iuh.engolearn.topic.controller;

import fit.iuh.engolearn.topic.dto.request.GetTopicItemPositionRequest;
import fit.iuh.engolearn.topic.dto.response.TopicItemPositionResponse;
import fit.iuh.engolearn.topic.dto.response.TopicItemProgressResponse;
import fit.iuh.engolearn.topic.dto.response.TopicProgressResponse;
import fit.iuh.engolearn.topic.exception.TopicItemNotFoundException;
import fit.iuh.engolearn.topic.exception.TopicNotFoundException;
import fit.iuh.engolearn.topic.model.enums.ItemProgressStatus;
import fit.iuh.engolearn.topic.model.enums.LearningStage;
import fit.iuh.engolearn.topic.service.TopicLearningService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests of the four learning endpoints with a mocked service.
 *
 * <p>What these tests prove: the routing of each endpoint, the JSON the frontend sees, that the
 * path id reaches the service, and how each failure is reported. What they do not prove: the
 * learning rules themselves, covered by the service tests.
 */
@WebMvcTest(TopicLearningController.class)
@org.springframework.context.annotation.Import(fit.iuh.engolearn.config.SecurityConfig.class)
class TopicLearningControllerTest {

    private static final Instant ACCESSED_AT = Instant.parse("2026-02-03T04:05:06Z");

    @MockitoBean private fit.iuh.engolearn.shared.security.impl.JwtTokenService tokens;
    @MockitoBean private fit.iuh.engolearn.user.repository.UserRepository users;
    @MockitoBean private fit.iuh.engolearn.user.repository.RefreshSessionRepository sessions;

    @MockitoBean private fit.iuh.engolearn.topic.service.TopicAttemptService attempts;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TopicLearningService topicLearningService;

    // --- content ------------------------------------------------------------------------

    @Test
    void retiredContentRejectsAuthenticatedReaders() throws Exception {
        mockMvc.perform(get("/api/v1/topic-items/i1/content").with(fit.iuh.engolearn.topic.support.TopicTestAuthentication.user()))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(topicLearningService, attempts);
    }

    @Test
    void retiredContentDoesNotConsultOptionalContent() throws Exception {
        mockMvc.perform(get("/api/v1/topic-items/i1/content").with(fit.iuh.engolearn.topic.support.TopicTestAuthentication.user()))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(topicLearningService, attempts);
    }

    @Test
    void retiredContentRejectsUnknownItemsWithGone() throws Exception {
        mockMvc.perform(get("/api/v1/topic-items/nope/content").with(fit.iuh.engolearn.topic.support.TopicTestAuthentication.user()))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(topicLearningService, attempts);
    }

    // --- position -----------------------------------------------------------------------

    @Test
    void positionReturnsTheStoredStageAndIndex() throws Exception {
        when(topicLearningService.getItemPosition(org.mockito.ArgumentMatchers.eq("user-mock-001"), any())).thenReturn(TopicItemPositionResponse.builder()
                .topicItemId("i1")
                .topicItemVersionId("iv1")
                .status(ItemProgressStatus.IN_PROGRESS)
                .stage(LearningStage.EXPRESSIONS)
                .itemIndex(1)
                .lastAccessedAt(ACCESSED_AT)
                .build());

        mockMvc.perform(get("/api/v1/topic-items/i1/position").with(fit.iuh.engolearn.topic.support.TopicTestAuthentication.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.topicItemId").value("i1"))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.stage").value("EXPRESSIONS"))
                .andExpect(jsonPath("$.data.itemIndex").value(1))
                .andExpect(jsonPath("$.data.lastAccessedAt").exists())
                .andExpect(jsonPath("$.timestamp").exists());

        ArgumentCaptor<GetTopicItemPositionRequest> request =
                ArgumentCaptor.forClass(GetTopicItemPositionRequest.class);
        verify(topicLearningService).getItemPosition(org.mockito.ArgumentMatchers.eq("user-mock-001"), request.capture());
        assertThat(request.getValue().getTopicItemId()).isEqualTo("i1");
    }

    @Test
    void positionOfANewItemIsNotStartedAndHasNoResumeMark() throws Exception {
        when(topicLearningService.getItemPosition(org.mockito.ArgumentMatchers.eq("user-mock-001"), any())).thenReturn(TopicItemPositionResponse.builder()
                .topicItemId("i1")
                .topicItemVersionId("iv1")
                .status(ItemProgressStatus.NOT_STARTED)
                .build());

        mockMvc.perform(get("/api/v1/topic-items/i1/position").with(fit.iuh.engolearn.topic.support.TopicTestAuthentication.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.data.stage").value(nullValue()))
                .andExpect(jsonPath("$.data.itemIndex").value(nullValue()));
    }

    @Test
    void retiredPositionWriteRejectsValidBodies() throws Exception {
        mockMvc.perform(put("/api/v1/topic-items/i1/position").with(fit.iuh.engolearn.topic.support.TopicTestAuthentication.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"VOCAB\",\"itemIndex\":2}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(topicLearningService, attempts);
    }

    @Test
    void savingThePositionWithoutABodyIsRejectedByControllerValidation() throws Exception {
        mockMvc.perform(put("/api/v1/topic-items/i1/position").with(fit.iuh.engolearn.topic.support.TopicTestAuthentication.user())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid learning request"));

        verifyNoInteractions(topicLearningService);
    }

    @Test
    void retiredPositionWriteDoesNotInvokeLearningRules() throws Exception {
        mockMvc.perform(put("/api/v1/topic-items/i2/position").with(fit.iuh.engolearn.topic.support.TopicTestAuthentication.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"CONTEXT\",\"itemIndex\":0}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(topicLearningService, attempts);
    }

    @Test
    void retiredPositionWriteDoesNotAttemptOptimisticMutation() throws Exception {
        mockMvc.perform(put("/api/v1/topic-items/i1/position").with(fit.iuh.engolearn.topic.support.TopicTestAuthentication.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"VOCAB\",\"itemIndex\":2}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(topicLearningService, attempts);
    }

    @Test
    void anUnknownStageInTheBodyIsABadRequestThatNeverReachesTheService() throws Exception {
        mockMvc.perform(put("/api/v1/topic-items/i1/position").with(fit.iuh.engolearn.topic.support.TopicTestAuthentication.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"NOT_A_STAGE\",\"itemIndex\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid learning request"));

        verifyNoInteractions(topicLearningService);
    }

    @Test
    void aBlankItemIdIsABadRequestThatNeverReachesTheService() throws Exception {
        mockMvc.perform(get("/api/v1/topic-items/ /content").with(fit.iuh.engolearn.topic.support.TopicTestAuthentication.user()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid learning request"))
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(topicLearningService);
    }

    @Test
    void aMissingStageInTheBodyIsABadRequestThatNeverReachesTheService() throws Exception {
        mockMvc.perform(put("/api/v1/topic-items/i1/position").with(fit.iuh.engolearn.topic.support.TopicTestAuthentication.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemIndex\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid learning request"))
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(topicLearningService);
    }

    @Test
    void aNegativeItemIndexInTheBodyIsABadRequestThatNeverReachesTheService() throws Exception {
        mockMvc.perform(put("/api/v1/topic-items/i1/position").with(fit.iuh.engolearn.topic.support.TopicTestAuthentication.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"VOCAB\",\"itemIndex\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid learning request"))
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(topicLearningService);
    }

    @Test
    void retiredPositionWriteRejectsUnknownItemsWithGone() throws Exception {
        mockMvc.perform(put("/api/v1/topic-items/nope/position").with(fit.iuh.engolearn.topic.support.TopicTestAuthentication.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"CONTEXT\",\"itemIndex\":0}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(topicLearningService, attempts);
    }

    // --- topic progress -----------------------------------------------------------------

    @Test
    void progressReturnsTheCountsAndTheStatusOfEveryVisibleItem() throws Exception {
        when(attempts.topicProgress(org.mockito.ArgumentMatchers.eq("user-mock-001"), any())).thenReturn(TopicProgressResponse.builder()
                .topicId("t1")
                .status(ItemProgressStatus.IN_PROGRESS)
                .completedItemCount(1)
                .totalItemCount(2)
                .lastAccessedAt(ACCESSED_AT)
                .items(List.of(
                        TopicItemProgressResponse.builder()
                                .topicItemId("i1")
                                .status(ItemProgressStatus.COMPLETED)
                                .firstTryCorrectCount(3)
                                .totalQuizCount(4)
                                .completedAt(ACCESSED_AT)
                                .build(),
                        TopicItemProgressResponse.builder()
                                .topicItemId("i2")
                                .status(ItemProgressStatus.IN_PROGRESS)
                                .build()))
                .build());

        mockMvc.perform(get("/api/v1/topics/t1/progress").with(fit.iuh.engolearn.topic.support.TopicTestAuthentication.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.topicId").value("t1"))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.completedItemCount").value(1))
                .andExpect(jsonPath("$.data.totalItemCount").value(2))
                .andExpect(jsonPath("$.data.lastAccessedAt").exists())
                .andExpect(jsonPath("$.data.items[0].topicItemId").value("i1"))
                .andExpect(jsonPath("$.data.items[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.items[1].topicItemId").value("i2"))
                .andExpect(jsonPath("$.data.items[1].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(attempts).topicProgress("user-mock-001", "t1");
    }

    @Test
    void progressOfAnUnknownTopicIsReportedAsNotFound() throws Exception {
        when(attempts.topicProgress(org.mockito.ArgumentMatchers.eq("user-mock-001"), any()))
                .thenThrow(new TopicNotFoundException("Topic nope was not found"));

        mockMvc.perform(get("/api/v1/topics/nope/progress").with(fit.iuh.engolearn.topic.support.TopicTestAuthentication.user()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Topic not found"))
                .andExpect(jsonPath("$.status").value(404));
    }
}
