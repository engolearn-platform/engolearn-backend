package fit.iuh.engolearn.grammar.dto.response;

import java.time.Instant;
import java.util.List;

public record GrammarManagementTopicResponse(String id, String code, String titleVi, String titleEn, String description, String level,
                        Integer order, String status, long version, Instant updatedAt, Instant firstPublishedAt,
                        long readyCount, long lessonCount, List<GrammarManagementLessonResponse> lessons) {
}
