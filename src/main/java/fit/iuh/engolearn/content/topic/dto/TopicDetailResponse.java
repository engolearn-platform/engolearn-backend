package fit.iuh.engolearn.content.topic.dto;

import fit.iuh.engolearn.shared.model.CefrLevel;
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
    private String id;
    private String version;
    private String title;
    private String description;
    private CefrLevel cefrLevel;
    List<TopicItemSummaryResponse> topicItems;
}
