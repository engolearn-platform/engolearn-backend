package fit.iuh.engolearn.assessment.service.impl;
import fit.iuh.engolearn.assessment.service.*;

import fit.iuh.engolearn.assessment.contract.*;
import fit.iuh.engolearn.assessment.contract.dto.*;
import fit.iuh.engolearn.assessment.repository.AssessmentRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
@lombok.RequiredArgsConstructor
public class AssessmentContractsServiceImpl implements AssessmentResults,AssessmentGrantRevocation {
    private final AssessmentRepository store;
    private final AssessmentTransactions transactions;
    private final org.springframework.core.env.Environment environment;
    @Override public Optional<AssessmentResultDto> latest(String userId) {
        requireUser(userId);
        return store.valid(userId).stream().filter(a->development()||!a.getPaper().isDemonstration())
                .findFirst().map(this::result);
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
    @Override public void revoke(String userId,String sourceId) {
        if(userId==null||userId.isBlank()||sourceId==null||sourceId.isBlank()) throw new IllegalArgumentException("Grant source is required");
        transactions.execute(userId,()->{store.revoke(userId,sourceId);return null;});
    }
}
