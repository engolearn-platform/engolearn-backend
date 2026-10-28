package fit.iuh.engolearn.topic.dto.response;

import java.util.List;

public record QuizFillBlankResponse(String quizId, String title, int index, int totalInType, String situationVi,
                                    String imageUrl, String tips, String promptAudioUrl, String type,
                                    String sentenceBefore, String sentenceAfter, List<QuizOptionResponse> options)
        implements QuizResponse {
    public QuizFillBlankResponse {
        type = "FILL_BLANK";
    }
}
