package fit.iuh.engolearn.content.api;

import fit.iuh.engolearn.shared.model.CefrLevel;
import java.util.List;

public record TopicCardView(
        String topicId,
        Integer publishedVersion,
        String titleEn,
        String titleVi,
        String description,
        CefrLevel level,
        String category,
        Integer orderIndex,
        Integer durationMinutes,
        String coverUrl,
        List<String> tags,
        int totalItems,
        int totalWords) {
}
