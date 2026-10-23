package fit.iuh.engolearn.topic.controller;

import fit.iuh.engolearn.topic.dto.request.GetTopicItemContentRequest;
import fit.iuh.engolearn.topic.dto.request.GetTopicItemPositionRequest;
import fit.iuh.engolearn.topic.dto.request.GetTopicProgressRequest;
import fit.iuh.engolearn.topic.dto.request.SaveTopicItemPositionRequest;
import fit.iuh.engolearn.topic.dto.response.TopicContextResponse;
import fit.iuh.engolearn.topic.dto.response.TopicExpressionBlockResponse;
import fit.iuh.engolearn.topic.dto.response.TopicExpressionExampleResponse;
import fit.iuh.engolearn.topic.dto.response.TopicExpressionPurposeResponse;
import fit.iuh.engolearn.topic.dto.response.TopicItemContentResponse;
import fit.iuh.engolearn.topic.dto.response.TopicItemPositionResponse;
import fit.iuh.engolearn.topic.dto.response.TopicItemProgressResponse;
import fit.iuh.engolearn.topic.dto.response.TopicProgressResponse;
import fit.iuh.engolearn.topic.dto.response.TopicVocabularyResponse;
import fit.iuh.engolearn.topic.exception.LearningPreconditionFailedException;
import fit.iuh.engolearn.topic.exception.TopicItemNotFoundException;
import fit.iuh.engolearn.topic.exception.TopicNotFoundException;
import fit.iuh.engolearn.topic.model.enums.ItemProgressStatus;
import fit.iuh.engolearn.topic.model.enums.LearningStage;
import fit.iuh.engolearn.topic.service.TopicLearningService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.OptimisticLockingFailureException;
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
class TopicLearningControllerTest {

    private static final Instant ACCESSED_AT = Instant.parse("2026-02-03T04:05:06Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TopicLearningService topicLearningService;

    // --- content ------------------------------------------------------------------------

    @Test
    void contentReturnsTheContextVocabulariesAndExpressions() throws Exception {
        when(topicLearningService.getItemContent(any())).thenReturn(TopicItemContentResponse.builder()
                .topicItemId("i1")
                .topicItemVersionId("iv1")
                .version(1)
                .title("At the airport")
                .description("Check in for a flight")
                .context(TopicContextResponse.builder()
                        .scenarioDescription("At the airport")
                        .promptingQuestion("Where do you check in?")
                        .build())
                .vocabularies(List.of(TopicVocabularyResponse.builder()
                        .word("airport")
                        .pronunciation("/eapot/")
                        .meaningVi("san bay")
                        .audioUrl("airport.mp3")
                        .build()))
                .expressions(List.of(TopicExpressionBlockResponse.builder()
                        .purposes(List.of(TopicExpressionPurposeResponse.builder()
                                .purposeEn("Ask for help")
                                .purposeVi("Hoi xin tro giup")
                                .orderIndex(1)
                                .examples(List.of(TopicExpressionExampleResponse.builder()
                                        .textEn("Could you help me?")
                                        .textVi("Ban giup toi duoc khong?")
                                        .build()))
                                .build()))
                        .build()))
                .build());

        mockMvc.perform(get("/api/v1/topic-items/i1/content"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.topicItemId").value("i1"))
                .andExpect(jsonPath("$.data.topicItemVersionId").value("iv1"))
                .andExpect(jsonPath("$.data.context.scenarioDescription").value("At the airport"))
                .andExpect(jsonPath("$.data.vocabularies[0].word").value("airport"))
                .andExpect(jsonPath("$.data.vocabularies[0].meaningVi").value("san bay"))
                .andExpect(jsonPath("$.data.vocabularies[0].audioUrl").value("airport.mp3"))
                .andExpect(jsonPath("$.data.expressions[0].purposes[0].examples[0].textEn")
                        .value("Could you help me?"))
                .andExpect(jsonPath("$.timestamp").exists());

        ArgumentCaptor<GetTopicItemContentRequest> request =
                ArgumentCaptor.forClass(GetTopicItemContentRequest.class);
        verify(topicLearningService).getItemContent(request.capture());
        assertThat(request.getValue().getTopicItemId()).isEqualTo("i1");
    }

    @Test
    void contentWithoutTheOptionalPartsIsStillReturned() throws Exception {
        when(topicLearningService.getItemContent(any())).thenReturn(TopicItemContentResponse.builder()
                .topicItemId("i1")
                .topicItemVersionId("iv1")
                .title("At the airport")
                .context(TopicContextResponse.builder().scenarioDescription("At the airport").build())
                .build());

        mockMvc.perform(get("/api/v1/topic-items/i1/content"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vocabularies").value(nullValue()))
                .andExpect(jsonPath("$.data.expressions").value(nullValue()));
    }

    @Test
    void contentOfAnUnknownItemIsReportedAsNotFound() throws Exception {
        when(topicLearningService.getItemContent(any()))
                .thenThrow(new TopicItemNotFoundException("Topic item nope was not found"));

        mockMvc.perform(get("/api/v1/topic-items/nope/content"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Topic item not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Topic item nope was not found"));
    }

    // --- position -----------------------------------------------------------------------

    @Test
    void positionReturnsTheStoredStageAndIndex() throws Exception {
        when(topicLearningService.getItemPosition(any())).thenReturn(TopicItemPositionResponse.builder()
                .topicItemId("i1")
                .topicItemVersionId("iv1")
                .status(ItemProgressStatus.IN_PROGRESS)
                .stage(LearningStage.EXPRESSIONS)
                .itemIndex(1)
                .lastAccessedAt(ACCESSED_AT)
                .build());

        mockMvc.perform(get("/api/v1/topic-items/i1/position"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.topicItemId").value("i1"))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.stage").value("EXPRESSIONS"))
                .andExpect(jsonPath("$.data.itemIndex").value(1))
                .andExpect(jsonPath("$.data.lastAccessedAt").exists())
                .andExpect(jsonPath("$.timestamp").exists());

        ArgumentCaptor<GetTopicItemPositionRequest> request =
                ArgumentCaptor.forClass(GetTopicItemPositionRequest.class);
        verify(topicLearningService).getItemPosition(request.capture());
        assertThat(request.getValue().getTopicItemId()).isEqualTo("i1");
    }

    @Test
    void positionOfANewItemIsNotStartedAndHasNoResumeMark() throws Exception {
        when(topicLearningService.getItemPosition(any())).thenReturn(TopicItemPositionResponse.builder()
                .topicItemId("i1")
                .topicItemVersionId("iv1")
                .status(ItemProgressStatus.NOT_STARTED)
                .build());

        mockMvc.perform(get("/api/v1/topic-items/i1/position"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.data.stage").value(nullValue()))
                .andExpect(jsonPath("$.data.itemIndex").value(nullValue()));
    }

    @Test
    void savingThePositionSendsThePathIdAndTheBodyToTheService() throws Exception {
        when(topicLearningService.saveItemPosition(any())).thenReturn(TopicItemPositionResponse.builder()
                .topicItemId("i1")
                .topicItemVersionId("iv1")
                .status(ItemProgressStatus.IN_PROGRESS)
                .stage(LearningStage.VOCAB)
                .itemIndex(2)
                .lastAccessedAt(ACCESSED_AT)
                .build());

        mockMvc.perform(put("/api/v1/topic-items/i1/position")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"VOCAB\",\"itemIndex\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.stage").value("VOCAB"))
                .andExpect(jsonPath("$.data.itemIndex").value(2))
                .andExpect(jsonPath("$.timestamp").exists());

        ArgumentCaptor<SaveTopicItemPositionRequest> request =
                ArgumentCaptor.forClass(SaveTopicItemPositionRequest.class);
        verify(topicLearningService).saveItemPosition(request.capture());
        assertThat(request.getValue().getTopicItemId()).isEqualTo("i1");
        assertThat(request.getValue().getStage()).isEqualTo(LearningStage.VOCAB);
        assertThat(request.getValue().getItemIndex()).isEqualTo(2);
    }

    @Test
    void savingThePositionWithoutABodyIsRejectedByControllerValidation() throws Exception {
        mockMvc.perform(put("/api/v1/topic-items/i1/position")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid learning request"));

        verifyNoInteractions(topicLearningService);
    }

    @Test
    void savingAPositionThatALearningRuleBlocksIsUnprocessable() throws Exception {
        when(topicLearningService.saveItemPosition(any())).thenThrow(
                new LearningPreconditionFailedException("Topic item i1 must be completed first"));

        mockMvc.perform(put("/api/v1/topic-items/i2/position")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"CONTEXT\",\"itemIndex\":0}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Learning precondition failed"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value("Topic item i1 must be completed first"));
    }

    @Test
    void savingAPositionThatAnotherRequestChangedIsAConflict() throws Exception {
        when(topicLearningService.saveItemPosition(any())).thenThrow(
                new OptimisticLockingFailureException("the position was changed by another request"));

        mockMvc.perform(put("/api/v1/topic-items/i1/position")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"VOCAB\",\"itemIndex\":2}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Learning position changed"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void anUnknownStageInTheBodyIsABadRequestThatNeverReachesTheService() throws Exception {
        mockMvc.perform(put("/api/v1/topic-items/i1/position")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"NOT_A_STAGE\",\"itemIndex\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid learning request"));

        verifyNoInteractions(topicLearningService);
    }

    @Test
    void aBlankItemIdIsABadRequestThatNeverReachesTheService() throws Exception {
        mockMvc.perform(get("/api/v1/topic-items/ /content"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid learning request"))
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(topicLearningService);
    }

    @Test
    void aMissingStageInTheBodyIsABadRequestThatNeverReachesTheService() throws Exception {
        mockMvc.perform(put("/api/v1/topic-items/i1/position")
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
        mockMvc.perform(put("/api/v1/topic-items/i1/position")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"VOCAB\",\"itemIndex\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid learning request"))
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(topicLearningService);
    }

    @Test
    void aSavingPositionOfAnUnknownItemIsReportedAsNotFound() throws Exception {
        when(topicLearningService.saveItemPosition(any()))
                .thenThrow(new TopicItemNotFoundException("Topic item nope was not found"));

        mockMvc.perform(put("/api/v1/topic-items/nope/position")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"CONTEXT\",\"itemIndex\":0}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Topic item not found"));
    }

    // --- topic progress -----------------------------------------------------------------

    @Test
    void progressReturnsTheCountsAndTheStatusOfEveryVisibleItem() throws Exception {
        when(topicLearningService.getTopicProgress(any())).thenReturn(TopicProgressResponse.builder()
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

        mockMvc.perform(get("/api/v1/topics/t1/progress"))
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

        ArgumentCaptor<GetTopicProgressRequest> request =
                ArgumentCaptor.forClass(GetTopicProgressRequest.class);
        verify(topicLearningService).getTopicProgress(request.capture());
        assertThat(request.getValue().getTopicId()).isEqualTo("t1");
    }

    @Test
    void progressOfAnUnknownTopicIsReportedAsNotFound() throws Exception {
        when(topicLearningService.getTopicProgress(any()))
                .thenThrow(new TopicNotFoundException("Topic nope was not found"));

        mockMvc.perform(get("/api/v1/topics/nope/progress"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Topic not found"))
                .andExpect(jsonPath("$.status").value(404));
    }
}
