package fit.iuh.engolearn.grammar.dto.response;

import fit.iuh.engolearn.grammar.model.enums.GrammarLearningStatus;
import java.util.List;

public record GrammarLessonResponse(String topicId, String id, int number, String titleVi, String titleEn,
                                 String objective, GrammarLearningStatus status, int contentVersion,
                                 List<GrammarBlockResponse> blocks) {
}
