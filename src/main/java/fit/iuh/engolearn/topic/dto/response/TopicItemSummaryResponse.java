package fit.iuh.engolearn.topic.dto.response;

import fit.iuh.engolearn.user.contract.dto.UserProgressSummaryDto;
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
    private UserProgressSummaryDto currentUserProgress;
}
