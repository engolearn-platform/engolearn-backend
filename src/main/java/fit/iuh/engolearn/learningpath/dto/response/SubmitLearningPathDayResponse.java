package fit.iuh.engolearn.learningpath.dto.response;

import java.util.Date;

public record SubmitLearningPathDayResponse(
        String pathId, int weekNo, int dayNo, String status, Date closedAt,
        int assigned, int answered, int correct, int wrong,
        Double completionRate, Double practiceScore
) {}
