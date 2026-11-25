package fit.iuh.engolearn.speaking.model.enums;

public enum SpeakingAudioAssessmentStatus {
    PENDING, PROCESSING, ASSESSED, SKIPPED, FAILED;

    public boolean terminal() {
        return this != PENDING && this != PROCESSING;
    }
}
