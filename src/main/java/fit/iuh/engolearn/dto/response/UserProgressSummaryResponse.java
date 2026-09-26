package fit.iuh.engolearn.dto.response;

import java.time.Instant;

public class UserProgressSummaryResponse {
    private String lastTopicItemId;
    private Boolean isCompleted;
    private Instant completedAt;
    private Instant lastAccessedAt;
}
