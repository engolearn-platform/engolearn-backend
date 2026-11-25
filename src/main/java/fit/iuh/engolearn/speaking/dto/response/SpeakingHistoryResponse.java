package fit.iuh.engolearn.speaking.dto.response;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.speaking.model.enums.SpeakingAssessmentStatus;
import fit.iuh.engolearn.speaking.model.enums.SpeakingSessionStatus;
import fit.iuh.engolearn.topic.contract.dto.SpeakingScopeDto;
import fit.iuh.engolearn.topic.contract.dto.TopicSpeakingHistoryDto.LearningStatus;

import java.time.Instant;
import java.util.List;

public final class SpeakingHistoryResponse {
    private SpeakingHistoryResponse() {}

    public enum Group { IN_PROGRESS, COMPLETED, OTHER }

    public record Groups(List<Topic> items, int page, int size, boolean hasNext, Totals totals) {
        public Groups {
            items = List.copyOf(items);
        }
    }

    public record Totals(long inProgress, long completed, long other) {}

    public record Topic(String topicId, String title, CefrLevel level, String coverUrl, LearningStatus learningStatus,
                        Progress learningProgress, long sessionCount, Instant latestSessionCreatedAt) {}

    public record Progress(int completedItemCount, int totalItemCount, int percent) {}

    public record ParentSessions(List<Session> items, int page, int size, boolean hasNext,
                                 String topicId, String title, long sessionCount) {
        public ParentSessions {
            items = List.copyOf(items);
        }
    }

    public record Session(String sessionId, SpeakingScopeDto scope, Context context, Instant createdAt,
                          int committedTurnCount, long speechDurationMs, SpeakingSessionStatus status,
                          SpeakingAssessmentStatus assessmentStatus) {}

    public record Context(String title, String topicTitle, String topicItemTitle) {}
}
