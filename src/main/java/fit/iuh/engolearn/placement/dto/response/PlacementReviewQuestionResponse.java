package fit.iuh.engolearn.placement.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import fit.iuh.engolearn.placement.model.enums.PlacementSkill;
import java.util.List;

public record PlacementReviewQuestionResponse(String id, int order, PlacementSkill section, String prompt,
                                              String passage, List<PlacementOptionResponse> options,
                                              String selectedOptionId, String correctOptionId,
                                              @JsonInclude(JsonInclude.Include.NON_NULL) PlacementAudioResponse audio,
                                              @JsonInclude(JsonInclude.Include.NON_NULL) String explanation) {
}
