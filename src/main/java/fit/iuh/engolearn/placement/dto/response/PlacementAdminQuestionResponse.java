package fit.iuh.engolearn.placement.dto.response;

import fit.iuh.engolearn.placement.model.enums.PlacementLevel;
import fit.iuh.engolearn.placement.model.enums.PlacementQuestionStatus;
import fit.iuh.engolearn.placement.model.enums.PlacementSkill;
import java.util.List;

public record PlacementAdminQuestionResponse(String id, String code, PlacementSkill skill, PlacementLevel level,
                                              String prompt, String passage, List<OptionResponse> options,
                                              String correctOptionId, String explanation, String transcript,
                                              String audioUrl, PlacementQuestionStatus status, long revision,
                                              boolean mediaReady) {
    public record OptionResponse(String id, String label, String text) {}

    public record PageResponse(List<PlacementAdminQuestionResponse> items, long total, int page, int size) {}

    public record ImportResponse(int count) {}
}
