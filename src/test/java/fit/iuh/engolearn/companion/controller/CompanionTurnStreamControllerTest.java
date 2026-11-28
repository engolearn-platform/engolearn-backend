package fit.iuh.engolearn.companion.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import fit.iuh.engolearn.companion.dto.request.CompanionTurnRequest;
import fit.iuh.engolearn.companion.dto.response.MessageResponse;
import fit.iuh.engolearn.companion.exception.CompanionException;
import fit.iuh.engolearn.companion.service.CompanionTurnResult;
import fit.iuh.engolearn.companion.service.CompanionTurnService;
import fit.iuh.engolearn.config.CompanionProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CompanionTurnStreamControllerTest {
    private final CompanionTurnService turns = org.mockito.Mockito.mock(CompanionTurnService.class);
    private ExecutorService streamExecutor;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var properties = new CompanionProperties();
        properties.setTurnTimeout(Duration.ofSeconds(5));
        streamExecutor = Executors.newSingleThreadExecutor();
        mvc = MockMvcBuilders.standaloneSetup(new CompanionTurnStreamController(turns, properties, streamExecutor))
                .setControllerAdvice(new fit.iuh.engolearn.companion.controller.exceptionhandler.CompanionExceptionHandler())
                .build();
    }

    @AfterEach
    void closeExecutor() {
        streamExecutor.shutdownNow();
    }

    @Test
    void firstTurnEmitsCanonicalAdmissionBeforeModelEventsAndCompletion() throws Exception {
        UUID turnId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-25T00:00:00Z");
        var prepared = new CompanionTurnService.PreparedTurn("first-conversation", "learner-1", turnId.toString(), "Hello", 1, 1, new Object());
        var admission = new fit.iuh.engolearn.companion.dto.response.FirstTurnStatusResponse(turnId,
                fit.iuh.engolearn.companion.dto.response.FirstTurnStatusResponse.State.ACTIVE,
                new fit.iuh.engolearn.companion.dto.response.ConversationResponse("first-conversation", "Hello", now, now, 1, 100, 80),
                new MessageResponse("canonical-user", 1, "USER", "Hello", now), null, false);
        when(turns.prepareFirstTurn(any(CompanionTurnRequest.class))).thenReturn(new CompanionTurnService.FirstPreparedTurn(prepared, admission));
        doAnswer(invocation -> {
            CompanionTurnService.TurnObserver observer = invocation.getArgument(1);
            observer.onDelta("Answer");
            return new CompanionTurnResult(turnId.toString(), new MessageResponse("canonical-assistant", 2, "ASSISTANT", "Answer", now), 2, 100, false);
        }).when(turns).streamPreparedTurn(eq(prepared), any(CompanionTurnService.TurnObserver.class));
        MvcResult started = mvc.perform(post("/api/v1/companion/first-turns")
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM)
                .content("{\"turnId\":\"" + turnId + "\",\"content\":\"Hello\",\"retry\":false}"))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(started)).andExpect(status().isOk()).andExpect(result -> {
            String body = result.getResponse().getContentAsString();
            assertThat(occurrences(body, "event:admitted")).isEqualTo(1);
            assertThat(body).contains("canonical-user", "first-conversation", "event:delta", "event:completed");
            assertThat(body.indexOf("event:admitted")).isLessThan(body.indexOf("event:delta"));
        });
    }

    @Test
    void firstTurnExecutorRejectionAfterPreparationIsUnknownAdmissionNotRollback() throws Exception {
        UUID turnId = UUID.randomUUID();
        var prepared = new CompanionTurnService.PreparedTurn("first-conversation", "learner-1", turnId.toString(), "Hello", 1, 1, new Object());
        when(turns.prepareFirstTurn(any(CompanionTurnRequest.class))).thenReturn(new CompanionTurnService.FirstPreparedTurn(prepared, null));
        streamExecutor.shutdownNow();
        mvc.perform(post("/api/v1/companion/first-turns").contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .content("{\"turnId\":\"" + turnId + "\",\"content\":\"Hello\",\"retry\":false}"))
                .andExpect(status().isServiceUnavailable()).andExpect(content().string(org.hamcrest.Matchers.containsString("STREAM_CAPACITY_EXCEEDED")));
        org.mockito.Mockito.verify(turns).cancelPreparedTurn(prepared);
        org.mockito.Mockito.verify(turns, org.mockito.Mockito.never()).streamPreparedTurn(any(), any());
    }

    @Test
    void streamsDeltasAndOneCompletedEvent() throws Exception {
        UUID turnId = UUID.randomUUID();
        var prepared = new CompanionTurnService.PreparedTurn("conversation-1", "learner-1", turnId.toString(),
                "Hello", 1, 1, new java.util.concurrent.Semaphore(1));
        when(turns.prepareTurn(eq("conversation-1"), any(CompanionTurnRequest.class))).thenReturn(prepared);
        doAnswer(invocation -> {
            CompanionTurnService.TurnObserver observer = invocation.getArgument(1);
            observer.onStatus(CompanionTurnService.TurnStatus.ASSESSMENT_LOOKUP);
            observer.onDelta("Hello");
            return new CompanionTurnResult(turnId.toString(),
                    new MessageResponse("message-2", 2, "ASSISTANT", "Hello there", Instant.parse("2026-11-25T00:00:00Z")),
                    2, 100, false);
        }).when(turns).streamPreparedTurn(eq(prepared), any(CompanionTurnService.TurnObserver.class));

        MvcResult started = mvc.perform(post("/api/v1/companion/conversations/conversation-1/turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"turnId\":\"" + turnId + "\",\"content\":\"Hello\",\"retry\":false}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:delta")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:status")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data:{\"code\":\"ASSESSMENT_LOOKUP\"}")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:completed")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("event:error"))))
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    assertThat(occurrences(body, "event:status")).isEqualTo(2);
                    assertThat(occurrences(body, "data:{\"code\":\"PROCESSING\"}")).isEqualTo(1);
                    assertThat(occurrences(body, "data:{\"code\":\"ASSESSMENT_LOOKUP\"}")).isEqualTo(1);
                    assertThat(body.indexOf("PROCESSING")).isLessThan(body.indexOf("ASSESSMENT_LOOKUP"));
                    assertThat(body.indexOf("ASSESSMENT_LOOKUP")).isLessThan(body.indexOf("event:delta"));
                    assertThat(occurrences(body, "event:completed")).isEqualTo(1);
                });
    }

    @Test
    void mapsPreStreamAdmissionFailureToProblemDetail() throws Exception {
        when(turns.prepareTurn(eq("conversation-1"), any(CompanionTurnRequest.class)))
                .thenThrow(new CompanionException(409, "TURN_ACTIVE", "A turn is already active", false));

        mvc.perform(post("/api/v1/companion/conversations/conversation-1/turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"turnId\":\"" + UUID.randomUUID() + "\",\"content\":\"Hello\",\"retry\":false}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("TURN_ACTIVE")));
    }

    @Test
    void mapsPostCommitProviderFailureToOneSafeErrorEvent() throws Exception {
        UUID turnId = UUID.randomUUID();
        var prepared = new CompanionTurnService.PreparedTurn("conversation-1", "learner-1", turnId.toString(),
                "Hello", 1, 1, new java.util.concurrent.Semaphore(1));
        when(turns.prepareTurn(eq("conversation-1"), any(CompanionTurnRequest.class))).thenReturn(prepared);
        when(turns.streamPreparedTurn(eq(prepared), any(CompanionTurnService.TurnObserver.class)))
                .thenThrow(new CompanionException(503, "AI_PROVIDER_UNAVAILABLE", "The response could not be generated", true));

        MvcResult started = mvc.perform(post("/api/v1/companion/conversations/conversation-1/turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"turnId\":\"" + turnId + "\",\"content\":\"Hello\",\"retry\":false}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:error")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("AI_PROVIDER_UNAVAILABLE")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("event:completed"))))
                .andExpect(result -> assertThat(occurrences(result.getResponse().getContentAsString(), "event:error")).isEqualTo(1));
    }

    private int occurrences(String source, String fragment) {
        return (source.length() - source.replace(fragment, "").length()) / fragment.length();
    }
}
