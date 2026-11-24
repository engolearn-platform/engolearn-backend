package fit.iuh.engolearn.placement.mapper;

import fit.iuh.engolearn.placement.dto.response.PlacementAdminAttemptDetailResponse;
import fit.iuh.engolearn.placement.dto.response.PlacementAdminAttemptQuestionResponse;
import fit.iuh.engolearn.placement.dto.response.PlacementAdminAttemptSummaryResponse;
import fit.iuh.engolearn.placement.dto.response.PlacementAdminAttemptSummaryResponse.Kind;
import fit.iuh.engolearn.placement.dto.response.PlacementOptionResponse;
import fit.iuh.engolearn.placement.model.PlacementAttempt;
import fit.iuh.engolearn.placement.model.PlacementTestSet;
import fit.iuh.engolearn.placement.model.enums.PlacementClaimStatus;
import fit.iuh.engolearn.placement.model.enums.PlacementQuestionStatus;
import fit.iuh.engolearn.placement.model.enums.PlacementTestSetStatus;
import fit.iuh.engolearn.placement.model.vo.PlacementSnapshotItemVo;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PlacementAdminAttemptMapper {
    private PlacementAdminAttemptMapper() {
    }

    public static PlacementAdminAttemptSummaryResponse toSummary(PlacementAttempt attempt, String testSetCode,
            Integer totalQuestions, boolean hasResult, PlacementClaimStatus claimStatus) {
        String userId = attempt.getUserId();
        String attemptId = attempt.getId();
        String name = userId != null ? userId : "Khách #" + attemptId.substring(0, Math.min(8, attemptId.length()));
        return new PlacementAdminAttemptSummaryResponse(attemptId, name, userId != null ? Kind.USER : Kind.GUEST,
                userId, attempt.getTestSetId(), testSetCode != null ? testSetCode : attempt.getTestSetId(),
                attempt.getTestSetVersion(), attempt.getStartedAt(), attempt.getStatus(),
                hasResult ? attempt.getCorrectCount() : null, totalQuestions,
                hasResult ? attempt.getPlacementLevel() : null, claimStatus);
    }

    public static PlacementAdminAttemptDetailResponse toDetail(PlacementAttempt attempt, PlacementTestSet set,
            boolean hasResult, PlacementClaimStatus claimStatus) {
        List<PlacementAdminAttemptQuestionResponse> snapshot = toSnapshot(set, hasResult);
        var summary = toSummary(attempt, set == null ? null : set.getCode(),
                set == null || set.getItems() == null ? null : snapshot.size(), hasResult, claimStatus);
        Map<String, String> answers = new LinkedHashMap<>();
        if (attempt.getAnswers() != null) {
            attempt.getAnswers().stream().filter(Objects::nonNull)
                    .filter(answer -> answer.getQuestionId() != null)
                    .forEach(answer -> answers.put(answer.getQuestionId(), answer.getSelectedOptionId()));
        }
        return new PlacementAdminAttemptDetailResponse(summary.id(), summary.name(), summary.kind(), summary.userId(),
                summary.testSetId(), summary.testSetCode(), summary.testSetVersion(), summary.startedAt(),
                attempt.getDeadlineAt(), attempt.getEndedAt(), summary.status(), summary.correctCount(),
                summary.totalQuestions(), summary.level(), summary.claimStatus(), snapshot,
                Collections.unmodifiableMap(answers));
    }

    private static List<PlacementAdminAttemptQuestionResponse> toSnapshot(PlacementTestSet set, boolean hasResult) {
        if (set == null || set.getItems() == null) return List.of();
        PlacementQuestionStatus status = set.getStatus() == PlacementTestSetStatus.DRAFT
                ? PlacementQuestionStatus.DRAFT : PlacementQuestionStatus.ACTIVE;
        return set.getItems().stream().filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(PlacementSnapshotItemVo::getOrder))
                .map(item -> toQuestion(item, status, hasResult))
                .toList();
    }

    private static PlacementAdminAttemptQuestionResponse toQuestion(PlacementSnapshotItemVo item,
            PlacementQuestionStatus status, boolean hasResult) {
        List<PlacementOptionResponse> options = item.getOptions() == null ? List.of() : item.getOptions().stream()
                .filter(Objects::nonNull)
                .map(option -> new PlacementOptionResponse(option.getId(), option.getLabel(), option.getText()))
                .toList();
        String code = item.getCode() == null || item.getCode().isBlank() ? item.getId() : item.getCode();
        return new PlacementAdminAttemptQuestionResponse(item.getId(), code, item.getSkill(), item.getLevel(),
                text(item.getPrompt()), text(item.getPassage()), options,
                hasResult ? text(item.getCorrectOptionId()) : "", hasResult ? text(item.getExplanation()) : "",
                hasResult ? text(item.getTranscript()) : "", item.getAudio() == null ? "" : text(item.getAudio().getUrl()),
                status);
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
