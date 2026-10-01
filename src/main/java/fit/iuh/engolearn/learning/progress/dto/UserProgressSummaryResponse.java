package fit.iuh.engolearn.learning.progress.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProgressSummaryResponse {
    private String lastTopicItemId;
    private Boolean isCompleted;
    private Instant completedAt;
    private Instant lastAccessedAt;
}
