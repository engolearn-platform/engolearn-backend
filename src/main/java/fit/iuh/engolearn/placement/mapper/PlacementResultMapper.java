package fit.iuh.engolearn.placement.mapper;

import fit.iuh.engolearn.placement.dto.response.*;
import fit.iuh.engolearn.placement.model.PlacementAttempt;
import fit.iuh.engolearn.placement.model.enums.PlacementSkill;
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
                    item.getPassage(), options, answers.get(item.getId()), item.getCorrectOptionId(), audio, learnerExplanation(item));
        }).toList();
        return new PlacementReviewResponse(attempt.getId(), attempt.getStatus(), items.size(),
                attempt.getCorrectCount(), attempt.getPlacementLevel(), summary.incorrectCount(),
                summary.unansweredCount(), questions);
    }

    static String learnerExplanation(PlacementSnapshotItemVo item) {
        String explanation = item.getExplanation();
        if (explanation == null || explanation.isBlank()) return null;
        String text = explanation.strip();
        String source = item.getSkill() == PlacementSkill.LISTENING ? item.getTranscript() : item.getPassage();
        if (source == null || source.isBlank()) return text;
        var correctOption = item.getOptions().stream()
                .filter(option -> option.getId().equals(item.getCorrectOptionId())).findFirst().orElse(null);
        if (correctOption == null) return text;

        // The legacy seed wrapped the reason in the answer label and the complete source.
        String prefix = "Đáp án " + correctOption.getLabel() + ": " + correctOption.getText() + ". ";
        String suffix = " Ngữ liệu: “" + source + "”";
        if (text.length() < prefix.length() + suffix.length() || !text.startsWith(prefix) || !text.endsWith(suffix)) {
            return text;
        }
        String reason = text.substring(prefix.length(), text.length() - suffix.length()).strip();
        return reason.isEmpty() || reason.equals("Đối chiếu thông tin được nêu trong ngữ liệu riêng của câu này.")
                ? null : reason;
    }
}
