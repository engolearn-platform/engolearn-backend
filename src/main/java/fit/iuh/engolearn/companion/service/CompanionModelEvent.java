package fit.iuh.engolearn.companion.service;

import java.util.List;
import java.util.Objects;

/** Typed output from one streamed provider inference round. */
public sealed interface CompanionModelEvent
        permits CompanionModelEvent.TextDelta, CompanionModelEvent.ToolCalls,
                CompanionModelEvent.RoundCompleted {

    record TextDelta(String text) implements CompanionModelEvent {
        public TextDelta {
            Objects.requireNonNull(text, "text");
        }
    }

    /** Carries opaque provider turn state without exposing provider-native types to orchestration. */
    record ToolCalls(CompanionModelContinuation continuation, List<CompanionToolCall> calls)
            implements CompanionModelEvent {
        public ToolCalls {
            Objects.requireNonNull(continuation, "continuation");
            calls = List.copyOf(Objects.requireNonNull(calls, "calls"));
            if (calls.isEmpty()) {
                throw new IllegalArgumentException("At least one tool call is required");
            }
        }
    }

    /** Emitted once, after the provider has successfully completed this round. */
    record RoundCompleted(CompanionUsage usage) implements CompanionModelEvent {
        public RoundCompleted {
            Objects.requireNonNull(usage, "usage");
        }
    }
}
