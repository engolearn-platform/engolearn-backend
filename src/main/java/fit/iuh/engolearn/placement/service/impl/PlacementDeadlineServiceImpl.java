package fit.iuh.engolearn.placement.service.impl;

import fit.iuh.engolearn.placement.exception.PlacementRequestException;
import fit.iuh.engolearn.placement.model.PlacementAttempt;
import fit.iuh.engolearn.placement.model.PlacementTestSet;
import fit.iuh.engolearn.placement.model.enums.PlacementAttemptStatus;
import fit.iuh.engolearn.placement.model.enums.PlacementLevel;
import fit.iuh.engolearn.placement.repository.PlacementAttemptRepository;
import fit.iuh.engolearn.placement.repository.PlacementTestSetRepository;
import fit.iuh.engolearn.placement.service.PlacementDeadlineService;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlacementDeadlineServiceImpl implements PlacementDeadlineService {
    private static final Duration CLAIM_WINDOW = Duration.ofHours(2);
    private final PlacementAttemptRepository attempts;
    private final PlacementTestSetRepository testSets;

    @Override
    public PlacementAttempt resolve(PlacementAttempt attempt, Instant now) {
        while (attempt.getStatus() == PlacementAttemptStatus.IN_PROGRESS && !now.isBefore(attempt.getDeadlineAt())) {
            PlacementAttempt finished = finish(attempt, attempt.getAnswers().isEmpty()
                    ? PlacementAttemptStatus.EXPIRED : PlacementAttemptStatus.AUTO_SUBMITTED, attempt.getDeadlineAt());
            if (finished != null) return finished;
            attempt = attempts.findById(attempt.getId()).orElseThrow(() -> new PlacementRequestException(
                    HttpStatus.NOT_FOUND, "PLACEMENT_ATTEMPT_NOT_FOUND", "Placement attempt not found"));
        }
        return attempt;
    }

    @Override
    public PlacementAttempt submitBeforeDeadline(PlacementAttempt attempt, Instant now) {
        return finish(attempt, PlacementAttemptStatus.SUBMITTED, now);
    }

    private PlacementAttempt finish(PlacementAttempt attempt, PlacementAttemptStatus status, Instant endedAt) {
        if (status != PlacementAttemptStatus.EXPIRED && attempt.getUserId() != null
                && attempts.findValidResultForUser(attempt.getUserId()).isPresent()) {
            throw new PlacementRequestException(HttpStatus.CONFLICT, "PLACEMENT_USER_HAS_RESULT",
                    "This User already has a Placement result");
        }
        Integer score = null;
        PlacementLevel level = null;
        if (status != PlacementAttemptStatus.EXPIRED) {
            PlacementTestSet set = testSets.findVersion(attempt.getTestSetId(), attempt.getTestSetVersion())
                    .orElseThrow(() -> new PlacementRequestException(HttpStatus.CONFLICT,
                            "PLACEMENT_TEST_SET_UNAVAILABLE", "Assigned Placement test set version is unavailable"));
            Map<String, String> answers = attempt.getAnswers().stream().collect(Collectors.toMap(
                    a -> a.getQuestionId(), a -> a.getSelectedOptionId(), (first, last) -> last));
            score = (int) set.getItems().stream()
                    .filter(item -> item.getCorrectOptionId().equals(answers.get(item.getId()))).count();
            level = levelFor(score);
        }
        return attempts.finish(attempt.getId(), attempt.getRevision(), status, endedAt, score, level,
                attempt.getGuestSessionId() == null || status == PlacementAttemptStatus.EXPIRED
                        ? null : endedAt.plus(CLAIM_WINDOW)).orElse(null);
    }

    private PlacementLevel levelFor(int score) {
        if (score <= 5) return PlacementLevel.A1;
        if (score <= 10) return PlacementLevel.A2;
        if (score <= 15) return PlacementLevel.B1;
        return PlacementLevel.B2;
    }
}
