package fit.iuh.engolearn.topic.dto.response;

import java.util.List;

public record QuizSituationalChoiceResponse(String quizId, String title, int index, int totalInType,
                                            String situationVi, String imageUrl, String tips, String promptAudioUrl,
                                            String type, String questionVi, List<QuizOptionResponse> options)
        implements QuizResponse {
    public QuizSituationalChoiceResponse {
        type = "SITUATIONAL_CHOICE";
    }
}
