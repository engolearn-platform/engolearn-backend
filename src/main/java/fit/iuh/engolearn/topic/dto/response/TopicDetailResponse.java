package fit.iuh.engolearn.topic.dto.response;

import fit.iuh.engolearn.shared.enums.CefrLevel;
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
    private String topicId;
    private String titleEn;
    private String titleVi;
    private CefrLevel cefrLevel;
    private String description;
    private List<TopicItemSummaryResponse> items;
}
