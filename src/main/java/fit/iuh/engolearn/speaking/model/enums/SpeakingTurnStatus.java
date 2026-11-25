package fit.iuh.engolearn.speaking.model.enums;

public enum SpeakingTurnStatus {
    RECEIVING, TRANSCRIBING, GENERATING, COMMITTED, SKIPPED, FAILED;

    public boolean terminal() {
        return this == COMMITTED || this == SKIPPED || this == FAILED;
    }
}
