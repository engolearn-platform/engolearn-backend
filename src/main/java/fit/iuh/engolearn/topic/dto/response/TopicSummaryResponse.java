package fit.iuh.engolearn.topic.dto.response;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicSummaryResponse {
    private String topicId;
    private String coverUrl;
    private String titleEn;
    private String titleVi;
    private CefrLevel cefrLevel;
    private int vocabularyCount;
    private int quizCount;
    private int durationMinutes;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Integer sortOrderIndex;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private java.util.Map<String, String> visibleItemVersions;
    private int totalItemCount;
    private TopicCatalogPolicy.ProgressSummary progress;
    private boolean hasNewContent;
    private TopicCatalogPolicy.Availability startAvailability;
    private java.time.Instant lastAccessedAt;
}
