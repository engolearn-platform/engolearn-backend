package fit.iuh.engolearn.companion.service;

import fit.iuh.engolearn.companion.dto.request.CompanionTurnRequest;

public interface CompanionTurnService {
    PreparedTurn prepareTurn(String conversationId, CompanionTurnRequest request);
    CompanionTurnResult streamPreparedTurn(PreparedTurn preparedTurn, TurnObserver observer);
    void abandonPreparedTurn(PreparedTurn preparedTurn);
    default void cancelPreparedTurn(PreparedTurn preparedTurn) { abandonPreparedTurn(preparedTurn); }

    enum TurnStatus {
        ASSESSMENT_LOOKUP
    }

    interface TurnObserver {
        void onDelta(String text);
        boolean isCancelled();
        default void onStatus(TurnStatus status) {}
        default void setCancelAction(Runnable cancelAction) {}
    }

    final class PreparedTurn {
        private final String conversationId;
        private final String learnerId;
        private final String turnId;
        private final String content;
        private final long generation;
        private final long userSequence;
        private final Object guardToken;

        public PreparedTurn(String conversationId, String learnerId, String turnId, String content,
                            long generation, long userSequence, Object guardToken) {
            this.conversationId = conversationId;
            this.learnerId = learnerId;
            this.turnId = turnId;
            this.content = content;
            this.generation = generation;
            this.userSequence = userSequence;
            this.guardToken = guardToken;
        }
        public String conversationId() { return conversationId; }
        public String learnerId() { return learnerId; }
        public String turnId() { return turnId; }
        public String content() { return content; }
        public long generation() { return generation; }
        public long userSequence() { return userSequence; }
        public Object guardToken() { return guardToken; }
    }
}
