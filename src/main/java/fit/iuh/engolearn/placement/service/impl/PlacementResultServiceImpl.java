package fit.iuh.engolearn.placement.service.impl;

import fit.iuh.engolearn.placement.dto.response.*;
import fit.iuh.engolearn.placement.exception.PlacementRequestException;
import fit.iuh.engolearn.placement.mapper.PlacementResultMapper;
import fit.iuh.engolearn.placement.model.PlacementAttempt;
import fit.iuh.engolearn.placement.model.PlacementTestSet;
import fit.iuh.engolearn.placement.model.enums.PlacementAttemptStatus;
import fit.iuh.engolearn.placement.model.vo.PlacementSnapshotItemVo;
import fit.iuh.engolearn.placement.repository.PlacementAttemptRepository;
import fit.iuh.engolearn.placement.repository.PlacementTestSetRepository;
import fit.iuh.engolearn.placement.service.PlacementDeadlineService;
import fit.iuh.engolearn.placement.service.PlacementResultService;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlacementResultServiceImpl implements PlacementResultService {
    private final PlacementAttemptRepository attempts;
    private final PlacementTestSetRepository testSets;
    private final PlacementDeadlineService deadlines;
    private final Clock placementClock;

    @Override
    public PlacementResultResponse getResult(String attemptId, String currentUserId) {
        PreparedResult prepared = prepare(attemptId, currentUserId, "PLACEMENT_RESULT_NOT_AVAILABLE");
        return PlacementResultMapper.toResult(prepared.attempt(), prepared.items().size(), prepared.summary());
    }

    @Override
    public PlacementReviewResponse getReview(String attemptId, String currentUserId) {
        PreparedResult prepared = prepare(attemptId, currentUserId, "PLACEMENT_REVIEW_NOT_AVAILABLE");
        return PlacementResultMapper.toReview(prepared.attempt(), prepared.items(), prepared.answers(),
                prepared.summary());
    }

    private PreparedResult prepare(String attemptId, String currentUserId, String unavailableCode) {
        if (currentUserId == null) {
            throw new PlacementRequestException(HttpStatus.UNAUTHORIZED, "PLACEMENT_AUTHENTICATION_REQUIRED",
                    "Authentication is required to access Placement results");
        }
        PlacementAttempt attempt = attempts.findById(attemptId).orElseThrow(PlacementResultServiceImpl::notFound);
        if (!currentUserId.equals(attempt.getUserId())) throw notFound();
        attempt = deadlines.resolve(attempt, placementClock.instant());
        if ((attempt.getStatus() != PlacementAttemptStatus.SUBMITTED
                && attempt.getStatus() != PlacementAttemptStatus.AUTO_SUBMITTED)
                || attempt.getCorrectCount() == null || attempt.getPlacementLevel() == null
                || attempt.getEndedAt() == null) {
            throw new PlacementRequestException(HttpStatus.NOT_FOUND, unavailableCode,
                    "Placement result is not available");
        }
        PlacementTestSet set = testSets.findVersion(attempt.getTestSetId(), attempt.getTestSetVersion())
                .orElseThrow(() -> new PlacementRequestException(HttpStatus.CONFLICT,
                        "PLACEMENT_TEST_SET_UNAVAILABLE", "Assigned Placement test set version is unavailable"));
        List<PlacementSnapshotItemVo> items = orderedSnapshot(set);
        Map<String, String> answers = currentAnswers(attempt, items);
        int total = items.size();
        int score = attempt.getCorrectCount();
        int answered = (int) answers.values().stream().filter(value -> value != null).count();
        int incorrect = answered - score;
        int unanswered = total - answered;
        if (score < 0 || incorrect < 0 || unanswered < 0 || score + incorrect + unanswered != total) {
            throw inconsistent();
        }
        return new PreparedResult(attempt, items, answers,
                new PlacementResultSummaryResponse(incorrect, unanswered));
    }

    private List<PlacementSnapshotItemVo> orderedSnapshot(PlacementTestSet set) {
        if (set.getItems() == null || set.getItems().size() != 20
                || set.getItems().stream().anyMatch(Objects::isNull)) throw inconsistent();
        List<PlacementSnapshotItemVo> ordered = new ArrayList<>(set.getItems());
        ordered.sort(Comparator.comparingInt(PlacementSnapshotItemVo::getOrder));
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < ordered.size(); i++) {
            PlacementSnapshotItemVo item = ordered.get(i);
            if (item.getOrder() != i + 1 || item.getId() == null || !ids.add(item.getId())
                    || item.getOptions() == null || item.getCorrectOptionId() == null
                    || item.getOptions().stream().noneMatch(o -> item.getCorrectOptionId().equals(o.getId()))) {
                throw inconsistent();
            }
        }
        return ordered;
    }

    private Map<String, String> currentAnswers(PlacementAttempt attempt, List<PlacementSnapshotItemVo> items) {
        if (attempt.getAnswers() == null) throw inconsistent();
        Map<String, PlacementSnapshotItemVo> questions = new HashMap<>();
        items.forEach(item -> questions.put(item.getId(), item));
        Map<String, String> selected = new HashMap<>();
        attempt.getAnswers().forEach(answer -> {
            if (answer == null || !questions.containsKey(answer.getQuestionId())
                    || selected.containsKey(answer.getQuestionId())
                    || (answer.getSelectedOptionId() != null && questions.get(answer.getQuestionId())
                    .getOptions().stream().noneMatch(o -> o.getId().equals(answer.getSelectedOptionId())))) {
                throw inconsistent();
            }
            selected.put(answer.getQuestionId(), answer.getSelectedOptionId());
        });
        return selected;
    }

    private static PlacementRequestException inconsistent() {
        return new PlacementRequestException(HttpStatus.CONFLICT, "PLACEMENT_RESULT_INCONSISTENT",
                "Placement result and assigned test set are inconsistent");
    }

    private static PlacementRequestException notFound() {
        return new PlacementRequestException(HttpStatus.NOT_FOUND, "PLACEMENT_ATTEMPT_NOT_FOUND",
                "Placement attempt not found");
    }

    private record PreparedResult(PlacementAttempt attempt, List<PlacementSnapshotItemVo> items,
                                  Map<String, String> answers, PlacementResultSummaryResponse summary) {
    }
}
