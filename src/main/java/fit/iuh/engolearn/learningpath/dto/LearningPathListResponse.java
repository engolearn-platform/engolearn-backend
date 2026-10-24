package fit.iuh.engolearn.learningpath.dto;

import java.util.List;

public record LearningPathListResponse(
        List<LearningPathListItemResponse> items,
        SectionCounts counts
) {
    public record SectionCounts(
            long active,
            long completed,
            long cancelled
    ) {}
}
