package fit.iuh.engolearn.companion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fit.iuh.engolearn.companion.service.CompanionMemoryOperationCursor;
import fit.iuh.engolearn.companion.service.CompanionMemoryOperationEvent;
import fit.iuh.engolearn.companion.service.CompanionMemoryService;
import fit.iuh.engolearn.companion.service.CompanionMemoryToolContext;
import fit.iuh.engolearn.companion.service.CompanionTurnService;
import fit.iuh.engolearn.companion.service.MemoryMutation;
import fit.iuh.engolearn.companion.service.MemoryOperationResult;
import fit.iuh.engolearn.companion.service.MemoryRetrievalService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class CompanionMemoryToolHandlerTest {
    @Mock CompanionMemoryService memories;
    @Mock MemoryRetrievalService retrieval;
    CompanionMemoryToolHandler handler;
    private final List<CompanionMemoryOperationEvent> events = new java.util.ArrayList<>();

    @BeforeEach void setUp() {
        handler = new CompanionMemoryToolHandler(memories, retrieval, JsonMapper.builder().build(), "test-signing-secret");
    }

    private void stubMutation() {
        when(memories.mutate(any(), any())).thenAnswer(invocation -> {
            MemoryMutation mutation = invocation.getArgument(0);
            return new MemoryOperationResult("receipt-1", mutation.action(), "memory-1", 1, true, false);
        });
    }

    @Test void explicitSourceLabelDoesNotAuthorizeAnAutomaticWriteWhenDisabled() {
        String input = "I like drinking coffee.";
        String response = handler.execute("createMemory", Map.of("category", "PREFERENCE", "content", input,
                "source", "EXPLICIT", "trigger", "AUTOMATIC", "evidence", input), "learner-1", context(input, false));

        assertThat(response).contains("FORBIDDEN");
        verify(memories, never()).mutate(any(), any());
        assertThat(events).isEmpty();
    }

    @Test void explicitRememberRequestWorksWhileAutomaticMemoryIsOffAndKeepsParaphrase() {
        stubMutation();
        String input = "Bro, remember that I like coffee, okay?";
        String content = "The learner enjoys coffee.";
        String response = handler.execute("createMemory", Map.of("category", "PREFERENCE", "content", content,
                "source", "EXPLICIT", "trigger", "USER_REQUESTED", "evidence", input), "learner-1", context(input, false));

        var mutation = org.mockito.ArgumentCaptor.forClass(MemoryMutation.class);
        verify(memories).mutate(mutation.capture(), any());
        assertThat(response).contains("SUCCESS");
        assertThat(mutation.getValue().automatic()).isFalse();
        assertThat(mutation.getValue().content()).isEqualTo(content);
        assertThat(mutation.getValue().source()).isEqualTo(fit.iuh.engolearn.companion.model.enums.MemorySource.EXPLICIT);
        assertThat(events).containsExactly(new CompanionMemoryOperationEvent("receipt-1", "CREATED", "memory-1", 1));
    }

    @Test void automaticInferenceCanParaphraseCurrentInput() {
        stubMutation();
        String input = "I practice English every morning.";
        String content = "The learner has a morning English practice routine.";
        handler.execute("createMemory", Map.of("category", "OBSERVATION", "content", content,
                "source", "INFERRED", "trigger", "AUTOMATIC", "evidence", input), "learner-1", context(input, true));

        var mutation = org.mockito.ArgumentCaptor.forClass(MemoryMutation.class);
        verify(memories).mutate(mutation.capture(), any());
        assertThat(mutation.getValue().source()).isEqualTo(fit.iuh.engolearn.companion.model.enums.MemorySource.INFERRED);
        assertThat(mutation.getValue().automatic()).isTrue();
        assertThat(mutation.getValue().content()).isEqualTo(content);
    }

    @Test void negatedForgetRequestDoesNotAuthorizeDelete() {
        String input = "Đừng quên sở thích cà phê.";
        String response = handler.execute("deleteMemory", Map.of("memoryId", "memory-1", "expectedVersion", 1,
                "evidence", input), "learner-1", context(input, true));

        assertThat(response).contains("FORBIDDEN");
        verify(memories, never()).mutate(any(), any());
        assertThat(events).isEmpty();
    }

    @Test void accentedVietnameseDirectForgetRequestIsRecognized() {
        stubMutation();
        String input = "Hãy xóa ký ức về cà phê.";
        handler.execute("deleteMemory", Map.of("memoryId", "memory-1", "expectedVersion", 1,
                "evidence", input), "learner-1", context(input, false));

        var mutation = org.mockito.ArgumentCaptor.forClass(MemoryMutation.class);
        verify(memories).mutate(mutation.capture(), any());
        assertThat(mutation.getValue().action()).isEqualTo(MemoryMutation.Action.DELETED);
        assertThat(mutation.getValue().automatic()).isFalse();
    }

    @Test void evidenceMustBeVerbatimInCurrentInput() {
        String input = "I do not like coffee.";
        for (String evidence : List.of("I dislike coffee.", "I like tea.", "", "x".repeat(1001))) {
            String response = handler.execute("createMemory", Map.of("category", "PREFERENCE", "content", "Dislikes coffee",
                    "source", "EXPLICIT", "trigger", "USER_REQUESTED", "evidence", evidence), "learner-1", context(input, true));
            assertThat(response).contains("INVALID_ARGUMENTS");
        }
        verify(memories, never()).mutate(any(), any());
        assertThat(events).isEmpty();
    }

    @Test void semanticValidationIsNotClaimedByBackend() {
        stubMutation();
        String input = "I do not like coffee.";
        String content = "The learner dislikes coffee.";
        handler.execute("createMemory", Map.of("category", "PREFERENCE", "content", content,
                "source", "EXPLICIT", "trigger", "AUTOMATIC", "evidence", input), "learner-1", context(input, true));
        var mutation = org.mockito.ArgumentCaptor.forClass(MemoryMutation.class);
        verify(memories).mutate(mutation.capture(), any());
        assertThat(mutation.getValue().content()).isEqualTo(content);
    }

    @Test void missingOrUnknownTriggerIsRejectedForBothWrites() {
        String input = "I like tea.";
        for (String trigger : List.of("", "UNKNOWN")) {
            var create = new java.util.HashMap<String, Object>(Map.of("category", "PREFERENCE", "content", input,
                    "source", "EXPLICIT", "evidence", input));
            var update = new java.util.HashMap<String, Object>(Map.of("memoryId", "memory-1", "expectedVersion", 1,
                    "content", input, "evidence", input));
            if (!trigger.isEmpty()) {
                create.put("trigger", trigger);
                update.put("trigger", trigger);
            }
            assertThat(handler.execute("createMemory", create, "learner-1", context(input, true))).contains("INVALID_ARGUMENTS");
            assertThat(handler.execute("updateMemory", update, "learner-1", context(input, true))).contains("INVALID_ARGUMENTS");
        }
        verify(memories, never()).mutate(any(), any());
    }

    @Test void updateUsesModelTriggerAndPreservesParaphraseAndExpectedVersion() {
        stubMutation();
        String input = "Actually, please change that: I now prefer tea.";
        String content = "The learner now prefers tea.";
        String response = handler.execute("updateMemory", Map.of("memoryId", "memory-1", "expectedVersion", 3,
                "content", content, "trigger", "USER_REQUESTED", "evidence", input), "learner-1", context(input, false));
        assertThat(response).contains("SUCCESS");
        var mutation = org.mockito.ArgumentCaptor.forClass(MemoryMutation.class);
        verify(memories).mutate(mutation.capture(), any());
        assertThat(mutation.getValue().action()).isEqualTo(MemoryMutation.Action.UPDATED);
        assertThat(mutation.getValue().automatic()).isFalse();
        assertThat(mutation.getValue().content()).isEqualTo(content);
        assertThat(mutation.getValue().expectedVersion()).isEqualTo(3L);
        assertThat(mutation.getValue().source()).isNull();
        assertThat(mutation.getValue().category()).isNull();
    }

    @Test void automaticUpdateIsBlockedWhileOffAndFabricatedEvidenceIsRejected() {
        String input = "I now prefer tea.";
        var args = new java.util.HashMap<String, Object>(Map.of("memoryId", "memory-1", "expectedVersion", 1,
                "content", "Prefers tea", "trigger", "AUTOMATIC", "evidence", input));
        assertThat(handler.execute("updateMemory", args, "learner-1", context(input, false))).contains("FORBIDDEN");
        args.put("evidence", "I prefer coffee.");
        assertThat(handler.execute("updateMemory", args, "learner-1", context(input, true))).contains("INVALID_ARGUMENTS");
        verify(memories, never()).mutate(any(), any());
    }

    @Test void changingTriggerChangesOperationFingerprint() {
        stubMutation();
        String input = "Remember that I like tea.";
        var args = new java.util.HashMap<String, Object>(Map.of("category", "PREFERENCE", "content", "Likes tea",
                "source", "EXPLICIT", "trigger", "USER_REQUESTED", "evidence", input));
        handler.execute("createMemory", args, "learner-1", context(input, true));
        args.put("trigger", "AUTOMATIC");
        handler.execute("createMemory", args, "learner-1", context(input, true));
        var mutation = org.mockito.ArgumentCaptor.forClass(MemoryMutation.class);
        verify(memories, org.mockito.Mockito.times(2)).mutate(mutation.capture(), any());
        assertThat(mutation.getAllValues().get(0).fingerprint()).isNotEqualTo(mutation.getAllValues().get(1).fingerprint());
    }

    @Test void failedCanonicalMutationNeverEmitsSuccessEvent() {
        when(memories.mutate(any(), any())).thenThrow(new fit.iuh.engolearn.companion.exception.CompanionMemoryException(
                503, "MEMORY_STORAGE_UNAVAILABLE", "Storage unavailable", true));
        String input = "Remember my tea preference.";
        String result = handler.execute("createMemory", Map.of("category", "PREFERENCE", "content", "Prefers tea",
                "source", "EXPLICIT", "trigger", "USER_REQUESTED", "evidence", input), "learner-1", context(input, false));
        assertThat(result).contains("ERROR").doesNotContain("SUCCESS");
        assertThat(events).isEmpty();
    }

    @Test void replayOfForgottenMemoryDoesNotEmitSuccessEvent() {
        when(memories.mutate(any(), any())).thenReturn(new MemoryOperationResult("receipt-1",
                MemoryMutation.Action.CREATED, "memory-1", 1, false, true));
        String input = "Remember my tea preference.";
        String result = handler.execute("createMemory", Map.of("category", "PREFERENCE", "content", "Prefers tea",
                "source", "EXPLICIT", "trigger", "USER_REQUESTED", "evidence", input), "learner-1", context(input, false));
        assertThat(result).contains("NO_DATA");
        assertThat(events).isEmpty();
    }

    private CompanionMemoryToolContext context(String input, boolean enabled) {
        return new CompanionMemoryToolContext("conversation-1", "turn-1", input, enabled, 0,
                Instant.now().plusSeconds(30), () -> {}, new CompanionMemoryOperationCursor(),
                new CompanionTurnService.TurnObserver() {
                    @Override public void onDelta(String text) {}
                    @Override public boolean isCancelled() { return false; }
                    @Override public void onMemoryOperation(CompanionMemoryOperationEvent event) { events.add(event); }
                });
    }
}
