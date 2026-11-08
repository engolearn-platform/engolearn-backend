package fit.iuh.engolearn.placement.service.impl;

import fit.iuh.engolearn.placement.dto.response.*;
import fit.iuh.engolearn.placement.exception.PlacementRequestException;
import fit.iuh.engolearn.placement.mapper.PlacementAttemptMapper;
import fit.iuh.engolearn.placement.model.PlacementAttempt;
import fit.iuh.engolearn.placement.model.PlacementTestSet;
import fit.iuh.engolearn.placement.model.enums.*;
import fit.iuh.engolearn.placement.model.vo.PlacementSnapshotItemVo;
import fit.iuh.engolearn.placement.repository.PlacementAttemptRepository;
import fit.iuh.engolearn.placement.repository.PlacementStartSelection;
import fit.iuh.engolearn.placement.repository.PlacementTestSetRepository;
import fit.iuh.engolearn.placement.service.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlacementAttemptServiceImpl implements PlacementAttemptService {
    private static final Duration DURATION = Duration.ofMinutes(15);

    private final PlacementAttemptRepository attempts;
    private final PlacementTestSetRepository testSets;
    private final Clock placementClock;
    private final PlacementDeadlineService deadlines;

    @Override
    public boolean isKnownGuestSession(String sessionId) {
        return attempts.isKnownGuestSession(sessionId);
    }

    @Override
    public PlacementStartOutcome start(PlacementAccessContext access) {
        Instant now = placementClock.instant();
        String guest = access.guestSessionId();
        String user = access.currentUserId();
        PlacementAttempt existing = findExisting(access, now);
        if (existing != null && existing.getStatus() != PlacementAttemptStatus.EXPIRED) return outcome(existing, false);
        if (user == null && guest == null) throw new PlacementRequestException(HttpStatus.BAD_REQUEST,
                "PLACEMENT_GUEST_SESSION_REQUIRED", "A Guest session is required to start Placement");
        PlacementTestSet set = testSets.findPublished().orElseThrow(() -> new PlacementRequestException(
                HttpStatus.UNPROCESSABLE_ENTITY, "PLACEMENT_TEST_SET_UNAVAILABLE", "No published Placement test set is available"));
        PlacementAttempt proposed = PlacementAttempt.builder().id(UUID.randomUUID().toString())
                .userId(user).guestSessionId(user == null ? guest : null)
                .testSetId(set.getId()).testSetVersion(set.getVersion())
                .status(PlacementAttemptStatus.IN_PROGRESS).startedAt(now).deadlineAt(now.plus(DURATION))
                .answers(new ArrayList<>()).build();
        PlacementStartSelection selection = attempts.startIfEligible(proposed, guest, now);
        PlacementAttempt selected = selection.attempt();
        if (!selection.created()) selected = deadlines.resolve(selected, now);
        if (!selection.created() && selected.getStatus() == PlacementAttemptStatus.EXPIRED) {
            return start(access);
        }
        if (selected.getUserId() != null && !selected.getUserId().equals(user)) {
            if (selected.getStatus() != PlacementAttemptStatus.IN_PROGRESS) return start(access);
            throw new PlacementRequestException(HttpStatus.CONFLICT, "PLACEMENT_ACTIVE_ATTEMPT_EXISTS",
                    "This session has an active Placement attempt owned by another identity");
        }
        return outcome(selected, selection.created());
    }

    private PlacementAttempt findExisting(PlacementAccessContext access, Instant now) {
        String guest = access.guestSessionId();
        String user = access.currentUserId();
        PlacementAttempt expired = null;
        if (guest != null && attempts.isKnownGuestSession(guest)) {
            PlacementAttempt activeGuest = attempts.findActiveForGuest(guest).orElse(null);
            if (activeGuest != null) {
                PlacementAttempt resolved = deadlines.resolve(activeGuest, now);
                if (resolved.getStatus() == PlacementAttemptStatus.IN_PROGRESS) return resolved;
                if (resolved.getStatus() == PlacementAttemptStatus.EXPIRED) expired = resolved;
            }
        }
        if (user != null) {
            PlacementAttempt result = attempts.findValidResultForUser(user).orElse(null);
            if (result != null) return result;
            PlacementAttempt active = attempts.findActiveForUser(user).orElse(null);
            if (active != null) {
                PlacementAttempt resolved = deadlines.resolve(active, now);
                if (resolved.getStatus() == PlacementAttemptStatus.IN_PROGRESS) return resolved;
                if (resolved.getStatus() == PlacementAttemptStatus.EXPIRED) expired = resolved;
                result = attempts.findValidResultForUser(user).orElse(null);
                if (result != null) return result;
            }
        }
        if (guest != null && attempts.isKnownGuestSession(guest)) {
            PlacementAttempt blocking = attempts.findClaimableForGuest(guest, now).orElse(null);
            if (blocking != null) return blocking;
        }
        return expired;
    }

    @Override
    public PlacementCurrentAttemptResponse current(PlacementAccessContext access) {
        Instant now = placementClock.instant();
        PlacementAttempt attempt = findExisting(access, now);
        if (attempt == null) return null;
        PlacementTestSet set = requireSet(attempt);
        var answered = attempt.getAnswers().stream()
                .filter(a -> a.getSelectedOptionId() != null)
                .map(a -> a.getQuestionId()).collect(java.util.stream.Collectors.toSet());
        var ordered = set.getItems().stream().sorted(java.util.Comparator.comparingInt(PlacementSnapshotItemVo::getOrder)).toList();
        PlacementSnapshotItemVo next = ordered.stream().filter(q -> !answered.contains(q.getId()))
                .findFirst().orElse(ordered.getLast());
        Instant lastSaved = attempt.getAnswers().stream().map(a -> a.getAnsweredAt())
                .filter(java.util.Objects::nonNull).max(Instant::compareTo).orElse(null);
        return new PlacementCurrentAttemptResponse(attempt.getId(), attempt.getStatus(), attempt.getDeadlineAt(), now,
                ordered.size(), answered.size(), next.getSkill(), next.getOrder(), lastSaved, claimRequired(attempt));
    }

    @Override
    public PlacementAttemptResponse get(String attemptId, PlacementAccessContext access) {
        Instant now = placementClock.instant();
        PlacementAttempt attempt = deadlines.resolve(requireOwned(attemptId, access), now);
        return PlacementAttemptMapper.toAttempt(attempt, requireSet(attempt), now);
    }

    @Override
    public SavePlacementAnswerResponse saveAnswer(String attemptId, String questionId, String selectedOptionId,
                                                  PlacementAccessContext access) {
        for (;;) {
            Instant now = placementClock.instant();
            PlacementAttempt attempt = deadlines.resolve(requireOwned(attemptId, access), now);
            if (attempt.getStatus() != PlacementAttemptStatus.IN_PROGRESS) throw finalized();
            PlacementSnapshotItemVo question = requireSet(attempt).getItems().stream()
                    .filter(item -> item.getId().equals(questionId)).findFirst().orElseThrow(() ->
                            new PlacementRequestException(HttpStatus.BAD_REQUEST, "PLACEMENT_INVALID_QUESTION",
                                    "Question is not part of this Placement test set"));
            if (selectedOptionId != null && question.getOptions().stream()
                    .noneMatch(option -> option.getId().equals(selectedOptionId))) {
                throw new PlacementRequestException(HttpStatus.BAD_REQUEST, "PLACEMENT_INVALID_OPTION",
                        "Option is not part of this question");
            }
            if (attempts.saveAnswer(attemptId, attempt.getRevision(), questionId, selectedOptionId, now).isPresent()) {
                return new SavePlacementAnswerResponse(questionId, selectedOptionId);
            }
        }
    }

    @Override
    public PlacementSubmitResponse submit(String attemptId, PlacementAccessContext access) {
        for (;;) {
            Instant now = placementClock.instant();
            PlacementAttempt attempt = deadlines.resolve(requireOwned(attemptId, access), now);
            if (attempt.getStatus() != PlacementAttemptStatus.IN_PROGRESS) return submitted(attempt);
            if (attempt.getAnswers().isEmpty()) throw new PlacementRequestException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "PLACEMENT_NO_ANSWER_TO_SUBMIT", "Bạn chưa trả lời câu hỏi nào");
            PlacementAttempt finished = deadlines.submitBeforeDeadline(attempt, now);
            if (finished != null) return submitted(finished);
        }
    }

    private PlacementAttempt requireOwned(String id, PlacementAccessContext access) {
        PlacementAttempt attempt = attempts.findById(id).orElseThrow(PlacementAttemptServiceImpl::notFound);
        if (attempt.getUserId() != null ? !attempt.getUserId().equals(access.currentUserId())
                : !attempt.getGuestSessionId().equals(access.guestSessionId())) throw notFound();
        return attempt;
    }

    private PlacementTestSet requireSet(PlacementAttempt attempt) {
        return testSets.findVersion(attempt.getTestSetId(), attempt.getTestSetVersion()).orElseThrow(() ->
                new PlacementRequestException(HttpStatus.CONFLICT, "PLACEMENT_TEST_SET_UNAVAILABLE",
                        "Assigned Placement test set version is unavailable"));
    }

    private static PlacementStartOutcome outcome(PlacementAttempt attempt, boolean created) {
        return new PlacementStartOutcome(new StartPlacementAttemptResponse(attempt.getId(), attempt.getStatus(),
                attempt.getDeadlineAt(), claimRequired(attempt)), created);
    }

    private static PlacementSubmitResponse submitted(PlacementAttempt attempt) {
        return new PlacementSubmitResponse(attempt.getId(), attempt.getStatus(), attempt.getAnswers().size(),
                claimRequired(attempt));
    }

    private static boolean claimRequired(PlacementAttempt attempt) {
        return attempt.getGuestSessionId() != null && attempt.getUserId() == null;
    }

    private static PlacementRequestException finalized() {
        return new PlacementRequestException(HttpStatus.CONFLICT, "PLACEMENT_ATTEMPT_FINALIZED",
                "Placement attempt is already finalized");
    }

    private static PlacementRequestException notFound() {
        return new PlacementRequestException(HttpStatus.NOT_FOUND, "PLACEMENT_ATTEMPT_NOT_FOUND",
                "Placement attempt not found");
    }
}
