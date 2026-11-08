package fit.iuh.engolearn.placement.mapper;

import fit.iuh.engolearn.placement.dto.response.*;
import fit.iuh.engolearn.placement.model.PlacementAttempt;
import fit.iuh.engolearn.placement.model.vo.PlacementSnapshotItemVo;
import java.util.List;
import java.util.Map;

public final class PlacementResultMapper {
    private PlacementResultMapper() {
    }

    public static PlacementResultResponse toResult(PlacementAttempt attempt, int total,
                                                    PlacementResultSummaryResponse summary) {
        return new PlacementResultResponse(attempt.getId(), attempt.getStatus(), attempt.getCorrectCount(),
                total, attempt.getPlacementLevel(), attempt.getEndedAt(), summary);
    }

    public static PlacementReviewResponse toReview(PlacementAttempt attempt, List<PlacementSnapshotItemVo> items,
                                                    Map<String, String> answers, PlacementResultSummaryResponse summary) {
        List<PlacementReviewQuestionResponse> questions = items.stream().map(item -> {
            List<PlacementOptionResponse> options = item.getOptions().stream()
                    .map(option -> new PlacementOptionResponse(option.getId(), option.getLabel(), option.getText()))
                    .toList();
            PlacementAudioResponse audio = item.getAudio() == null ? null : new PlacementAudioResponse(
                    item.getAudio().getUrl(), item.getAudio().getDurationSeconds());
            return new PlacementReviewQuestionResponse(item.getId(), item.getOrder(), item.getSkill(), item.getPrompt(),
                    item.getPassage(), options, answers.get(item.getId()), item.getCorrectOptionId(), audio, item.getExplanation());
        }).toList();
        return new PlacementReviewResponse(attempt.getId(), attempt.getStatus(), items.size(),
                attempt.getCorrectCount(), attempt.getPlacementLevel(), summary.incorrectCount(),
                summary.unansweredCount(), questions);
    }
}
