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
    private String title;
    private String description;
    private CefrLevel cefrLevel;
    private Integer orderIndex;
    private Integer totalWords;
    private Integer durationMinutes;
}
