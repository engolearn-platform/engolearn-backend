package fit.iuh.engolearn.dto.response;

import fit.iuh.engolearn.models.shared.enums.CefrLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicDetailResponse {
    private String title;
    private String description;
    private CefrLevel cefrLevel;
    List<TopicItemSummaryResponse> topicItems;
}
