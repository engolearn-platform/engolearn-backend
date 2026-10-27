package fit.iuh.engolearn.topic.dto.response;

public final class TopicCatalogPolicy {
    private TopicCatalogPolicy() {}

    public record Availability(boolean allowed, String reason) {
        public static Availability signInRequired() {
            return new Availability(false, "SIGN_IN_REQUIRED");
        }
    }

    public record ItemActions(Availability start, @com.fasterxml.jackson.annotation.JsonProperty("continue") Availability continueAction, Availability review,
                              Availability retry, Availability resumeRetry, Availability restartRetry) {
        public static ItemActions anonymous() {
            Availability denied = Availability.signInRequired();
            return new ItemActions(denied, denied, denied, denied, denied, denied);
        }
    }

    public record ProgressSummary(String status, int completedItemCount, int totalItemCount, int percent) {}
    public record Position(String stage, int itemIndex) {}
    public record AttemptReference(String attemptId, Position position) {}
    public record RetrySession(String retrySessionId) {}
}
