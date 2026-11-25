package fit.iuh.engolearn.grammar.dto.response;

import fit.iuh.engolearn.grammar.model.enums.GrammarLearningStatus;

public record GrammarLessonSummaryResponse(String id, int number, String titleVi, String titleEn, String objective,
                                        GrammarLearningStatus status, boolean unlocked, Integer bestScore,
                                        boolean hasUpdate, int blockCount) {
}
