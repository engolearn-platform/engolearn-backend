package fit.iuh.engolearn.grammar.dto.response;

import java.time.Instant;
import java.util.List;

public record GrammarManagementLessonResponse(String id, String code, Integer order, String status, long version, boolean ready,
                         int contentVersion, String titleVi, String titleEn, String objective, List<String> tags,
                         List<GrammarContentBlockResponse> blocks, List<GrammarContentQuestionResponse> questions, boolean hasDraft, Instant firstPublishedAt,
                         String archiveReason) {
}
