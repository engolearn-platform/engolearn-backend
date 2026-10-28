package fit.iuh.engolearn.topic.dto.response;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicDetailResponse {
    private String topicId;
    private String titleEn;
    private String titleVi;
    private CefrLevel cefrLevel;
    private String description;
    private List<TopicItemSummaryResponse> items;
    private String coverUrl;
    private int durationMinutes;
    private int vocabularyCount;
    private int expressionCount;
    private int quizCount;
    private boolean hasNewContent;
    private TopicCatalogPolicy.ProgressSummary progress;
    private TopicCatalogPolicy.Availability retryAvailability;
    private TopicRetrySessionResponse retrySession;
}
