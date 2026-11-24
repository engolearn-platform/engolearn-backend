package fit.iuh.engolearn.grammar.dto.response;

import fit.iuh.engolearn.grammar.model.enums.GrammarPracticeStatus;
import java.time.Instant;
import java.util.List;

public record GrammarPracticeResponse(String id, String topicId, String lessonId, GrammarPracticeStatus status,
                                   int contentVersion, int answeredCount, int totalQuestions, GrammarQuestionResponse question,
                                   List<GrammarFeedbackResponse> feedback, GrammarResultResponse result, Instant expiresAt) {
}
