package fit.iuh.engolearn.content.api;

import fit.iuh.engolearn.shared.model.CefrLevel;
import java.util.List;

public record TopicDetailView(
        String topicId,
        Integer version,
        String titleEn,
        String titleVi,
        String description,
        CefrLevel level,
        String category,
        Integer orderIndex,
        Integer durationMinutes,
        String coverUrl,
        List<String> tags,
        List<TopicItemDetailView> items) {
}
