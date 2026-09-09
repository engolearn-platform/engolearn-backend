package fit.iuh.engolearn.dto.response;

import fit.iuh.engolearn.models.shared.enums.CefrLevel;
import fit.iuh.engolearn.models.shared.enums.ContentStatus;
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
