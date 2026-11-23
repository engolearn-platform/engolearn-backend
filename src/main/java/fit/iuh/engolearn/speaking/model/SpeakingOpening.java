package fit.iuh.engolearn.speaking.model;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public record SpeakingOpening(Status status, String attemptId, String reply, String hintExampleEn,
                              Set<String> retryRequestIds) {
    public enum Status { NOT_REQUIRED, GENERATING, SYNTHESIZING, READY, TEXT_ONLY, FAILED, CANCELLED }

    public SpeakingOpening {
        retryRequestIds = retryRequestIds == null ? Set.of() : Set.copyOf(retryRequestIds);
    }

    public static SpeakingOpening initial(SpeakingItemConfig.OpeningSide side) {
        return side == SpeakingItemConfig.OpeningSide.AI
                ? new SpeakingOpening(Status.GENERATING, UUID.randomUUID().toString(), null, null, Set.of())
                : new SpeakingOpening(Status.NOT_REQUIRED, null, null, null, Set.of());
    }

    public SpeakingOpening retry(String requestId) {
        Set<String> requests = new HashSet<>(retryRequestIds);
        requests.add(requestId);
        return new SpeakingOpening(Status.GENERATING, UUID.randomUUID().toString(), null, null, requests);
    }

    public SpeakingOpening withDraft(String reply, String hint) {
        return new SpeakingOpening(Status.SYNTHESIZING, attemptId, reply, hint, retryRequestIds);
    }

    public SpeakingOpening withStatus(Status next) {
        return new SpeakingOpening(next, attemptId, reply, hintExampleEn, retryRequestIds);
    }

    public boolean allowsTurn() {
        return status == Status.NOT_REQUIRED || status == Status.READY || status == Status.TEXT_ONLY;
    }

    public SpeakingOpening cancelPending() {
        return allowsTurn() ? this
                : new SpeakingOpening(Status.CANCELLED, attemptId, null, null, retryRequestIds);
    }
}
