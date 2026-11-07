package fit.iuh.engolearn.placement.dto.response;

import fit.iuh.engolearn.placement.model.enums.PlacementLevel;
import fit.iuh.engolearn.placement.model.enums.PlacementSkill;
import java.util.List;

public record PlacementQuestionResponse(String id, int order, PlacementSkill section, PlacementLevel level,
                                        String label, String prompt, String passage,
                                        List<PlacementOptionResponse> options, PlacementAudioResponse audio) {
}
