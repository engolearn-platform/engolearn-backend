package fit.iuh.engolearn.placement.mapper;

import fit.iuh.engolearn.placement.dto.response.*;
import fit.iuh.engolearn.placement.model.PlacementAttempt;
import fit.iuh.engolearn.placement.model.PlacementTestSet;
import fit.iuh.engolearn.placement.model.vo.PlacementSnapshotItemVo;
import java.time.Instant;
import java.util.List;

public final class PlacementAttemptMapper {
    private PlacementAttemptMapper() {
    }

    public static PlacementAttemptResponse toAttempt(PlacementAttempt attempt, PlacementTestSet set, Instant now) {
        List<PlacementQuestionResponse> questions = set.getItems().stream()
                .map(PlacementAttemptMapper::toQuestion).toList();
        List<PlacementSavedAnswerResponse> answers = attempt.getAnswers().stream()
                .map(a -> new PlacementSavedAnswerResponse(a.getQuestionId(), a.getSelectedOptionId())).toList();
        return new PlacementAttemptResponse(attempt.getId(), attempt.getStatus(), attempt.getStartedAt(),
                attempt.getDeadlineAt(), now, questions.size(), questions, answers,
                attempt.getGuestSessionId() != null && attempt.getUserId() == null);
    }

    private static PlacementQuestionResponse toQuestion(PlacementSnapshotItemVo item) {
        List<PlacementOptionResponse> options = item.getOptions().stream()
                .map(o -> new PlacementOptionResponse(o.getId(), o.getLabel(), o.getText())).toList();
        PlacementAudioResponse audio = item.getAudio() == null ? null :
                new PlacementAudioResponse(item.getAudio().getUrl(), item.getAudio().getDurationSeconds());
        return new PlacementQuestionResponse(item.getId(), item.getOrder(), item.getSkill(), item.getLevel(),
                item.getLabel(), item.getPrompt(), item.getPassage(), options, audio);
    }
}
