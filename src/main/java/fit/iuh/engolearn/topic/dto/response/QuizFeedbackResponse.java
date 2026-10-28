package fit.iuh.engolearn.topic.dto.response;

public record QuizFeedbackResponse(String correctOptionId, String completedSentenceEn, String translationVi,
                                  String explanationVi, String grammarNoteVi, String audioUrl) {}
