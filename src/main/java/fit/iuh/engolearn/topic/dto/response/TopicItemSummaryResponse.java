package fit.iuh.engolearn.topic.dto.response;

import fit.iuh.engolearn.dto.response.UserProgressSummaryResponse;
import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.topic.model.enums.ContentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicItemSummaryResponse {
    private String title;
    private String description;
    private CefrLevel cefrLevel;
    private Integer order;
    private ContentStatus status;
    private UserProgressSummaryResponse currentUserProgress;
}
