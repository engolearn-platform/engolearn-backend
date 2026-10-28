package fit.iuh.engolearn.topic.dto.response;

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
public class TopicExpressionPurposeResponse {
    private String purposeEn;
    private String purposeVi;
    private Integer orderIndex;
    private List<TopicExpressionExampleResponse> examples;
    private String purposeId;
}
