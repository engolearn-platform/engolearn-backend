package fit.iuh.engolearn.assessment.service.impl;
import fit.iuh.engolearn.assessment.service.*;

import fit.iuh.engolearn.assessment.contract.*;
import fit.iuh.engolearn.assessment.contract.dto.*;
import fit.iuh.engolearn.assessment.contract.enums.AssessmentCompanionScope;
import fit.iuh.engolearn.assessment.repository.AssessmentRepository;
import java.util.Optional;
import java.time.Instant;
import java.util.ArrayList;
import fit.iuh.engolearn.assessment.contract.enums.AssessmentCompanionHistoryCoverage;
import fit.iuh.engolearn.assessment.contract.exception.AssessmentHistoryInvalidQueryException;
import fit.iuh.engolearn.assessment.contract.exception.AssessmentHistoryUnavailableException;
import fit.iuh.engolearn.assessment.utils.AssessmentHistoryCursorCodec;
import fit.iuh.engolearn.assessment.utils.AssessmentHistoryCursorCodec.Position;
import org.springframework.stereotype.Service;

@Service
@lombok.RequiredArgsConstructor
public class AssessmentContractsServiceImpl implements AssessmentResults,AssessmentGrantRevocation,AssessmentCompanionResults {
    private final AssessmentRepository store;
    private final AssessmentTransactions transactions;
    private final org.springframework.core.env.Environment environment;
    private final AssessmentAccess access;
    @Override public Optional<AssessmentResultDto> latest(String userId) {
        requireUser(userId);
        return store.valid(userId).stream().filter(a->development()||!a.getPaper().isDemonstration())
                .findFirst().map(this::result);
    }
    @Override public Optional<AssessmentCompanionSummaryDto> latestForCompanion(String userId) {
        requireUser(userId);
        return store.valid(userId).stream().filter(a->development()||!a.getPaper().isDemonstration())
                .findFirst().map(a->companionResult(userId,a));
    }
    @Override
    public AssessmentCompanionHistoryPageDto historyForCompanion(String learnerId, AssessmentCompanionHistoryQueryDto query) {
        if (learnerId == null || learnerId.isBlank() || query == null || query.limit() < 2 || query.limit() > 5) {
            throw new AssessmentHistoryInvalidQueryException();
        }
        boolean includeDemo = development();
        Position position = query.cursor() == null ? null
                : AssessmentHistoryCursorCodec.decode(query.cursor(), learnerId, includeDemo, query.limit());
        Instant asOf = Instant.now();
        try {
            if (position != null && !store.companionHistoryAnchorExists(learnerId, includeDemo,
                    position.submittedAt(), position.attemptId())) {
                throw new AssessmentHistoryInvalidQueryException();
            }
            var attempts = store.companionHistory(learnerId, includeDemo,
                    position == null ? null : position.submittedAt(),
                    position == null ? null : position.attemptId(), query.limit() + 1);
            boolean hasMore = attempts.size() > query.limit();
            var items = new ArrayList<AssessmentCompanionHistoryRowDto>();
            for (var attempt : attempts.subList(0, Math.min(attempts.size(), query.limit()))) {
                var paper = attempt.getPaper();
                if (paper == null || paper.getPaperId() == null || paper.getPaperId().isBlank()
                        || attempt.getId() == null || attempt.getId().isBlank() || attempt.getSubmittedAt() == null
                        || (!includeDemo && paper.isDemonstration())) {
                    throw new AssessmentHistoryUnavailableException();
                }
                items.add(new AssessmentCompanionHistoryRowDto(companionResult(learnerId, attempt),
                        paper.getPaperId(), paper.getVersion(), paper.getRulesVersion(), paper.isDemonstration()));
            }
            String nextCursor = null;
            if (hasMore) {
                var last = items.getLast().result();
                nextCursor = AssessmentHistoryCursorCodec.encode(learnerId, includeDemo, query.limit(),
                        new Position(last.submittedAt(), last.attemptId()));
            }
            return new AssessmentCompanionHistoryPageDto(items, hasMore, nextCursor,
                    AssessmentCompanionHistoryCoverage.COMPLETE, asOf);
        } catch (AssessmentHistoryInvalidQueryException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AssessmentHistoryUnavailableException();
        }
    }

    @Override public Optional<AssessmentResultDto> findCompleted(String userId,String assessmentAttemptId) {
        requireUser(userId);
        if(assessmentAttemptId==null||assessmentAttemptId.isBlank()) throw new IllegalArgumentException("Assessment attempt ID is required");
        return Optional.ofNullable(store.completedAttempt(assessmentAttemptId,userId,development())).map(this::result);
    }
    @Override public java.util.Set<String> findCompletedAttemptIds(String userId,java.util.Set<String> assessmentAttemptIds) {
        requireUser(userId);
        if(assessmentAttemptIds==null||assessmentAttemptIds.stream().anyMatch(id->id==null||id.isBlank()))
            throw new IllegalArgumentException("Assessment attempt IDs are required");
        return java.util.Set.copyOf(store.completedAttemptIds(userId,assessmentAttemptIds,development()));
    }
    private boolean development() {
        var profiles=java.util.List.of(environment.getActiveProfiles());
        return profiles.contains("dev")&&!profiles.contains("prod");
    }
    private void requireUser(String userId) {
        if(userId==null||userId.isBlank()) throw new IllegalArgumentException("User ID is required");
    }
    private AssessmentResultDto result(fit.iuh.engolearn.assessment.model.AssessmentAttempt attempt) {
        return new AssessmentResultDto(attempt.getId(),attempt.getSubmittedAt(),attempt.getLevel(),
                attempt.getPaper()==null?null:attempt.getPaper().getPaperId(),
                attempt.getSkillResults().stream().map(skill->new AssessmentSkillDto(skill.getCode(),skill.getCorrect(),
                        skill.getTotal(),skill.getPercentage(),skill.getBand()==null?null:
                        fit.iuh.engolearn.assessment.contract.enums.AssessmentSkillBand.valueOf(skill.getBand().name()))).toList());
    }
    private AssessmentCompanionSummaryDto companionResult(String userId,
            fit.iuh.engolearn.assessment.model.AssessmentAttempt attempt) {
        boolean full=attempt.getAnalysisGrants().stream().anyMatch(source->!store.revoked(userId,source));
        if(!full) full=access.currentGrant(userId).isPresent();
        var skills=attempt.getSkillResults().stream().limit(full?5:2)
                .map(skill->new AssessmentCompanionSkillDto(skill.getCode(),skill.getName(),skill.getExample(),
                        skill.getSection()==null?null:skill.getSection().name(),skill.getCorrect(),skill.getTotal(),
                        skill.getPercentage(),skill.getBand()==null?null:
                        fit.iuh.engolearn.assessment.contract.enums.AssessmentSkillBand.valueOf(skill.getBand().name())))
                .toList();
        return new AssessmentCompanionSummaryDto(attempt.getId(),attempt.getSubmittedAt(),
                attempt.getCorrectCount()==null?null:attempt.getCorrectCount()*2,attempt.getLevel(),
                full?AssessmentCompanionScope.FULL:AssessmentCompanionScope.PREVIEW,skills);
    }
    @Override public void revoke(String userId,String sourceId) {
        if(userId==null||userId.isBlank()||sourceId==null||sourceId.isBlank()) throw new IllegalArgumentException("Grant source is required");
        transactions.execute(userId,()->{store.revoke(userId,sourceId);return null;});
    }
}
