package fit.iuh.engolearn.topic.dto.response;

import fit.iuh.engolearn.topic.model.enums.ExpressionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicExpressionExampleResponse {
    private ExpressionType type;
    private String textEn;
    private String textVi;
    private String usageNote;
    private String audioUrl;
}
