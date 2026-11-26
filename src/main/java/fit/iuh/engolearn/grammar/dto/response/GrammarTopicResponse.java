package fit.iuh.engolearn.grammar.dto.response;

import fit.iuh.engolearn.grammar.model.enums.GrammarLevel;

public record GrammarTopicResponse(String id, String titleVi, String titleEn, String description, GrammarLevel level,
                                String status, int completedLessons, int totalLessons, int percent) {
}
