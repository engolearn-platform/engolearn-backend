package fit.iuh.engolearn.content.topic.dto;

import fit.iuh.engolearn.shared.model.CefrLevel;
import fit.iuh.engolearn.shared.model.ContentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicItemSummaryResponse {
    private String id;
    private String title;
    private String description;
    private CefrLevel cefrLevel;
    private Integer order;
    private ContentStatus status;
}
