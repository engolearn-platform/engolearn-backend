package fit.iuh.engolearn.topic.dto.response;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
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
}
