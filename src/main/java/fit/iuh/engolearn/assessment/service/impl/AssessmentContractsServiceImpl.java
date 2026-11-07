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
        var profiles=java.util.List.of(environment.getActiveProfiles());
        boolean development=profiles.contains("dev")&&!profiles.contains("prod");
        return store.valid(userId).stream().filter(a->development||!a.getPaper().isDemonstration()).findFirst().map(a->new AssessmentResultDto(a.getId(),a.getSubmittedAt(),a.getLevel(),
                a.getSkillResults().stream().map(s->new AssessmentSkillDto(s.getCode(),s.getCorrect(),s.getTotal())).toList()));
    }
    @Override public void revoke(String userId,String sourceId) {
        if(userId==null||userId.isBlank()||sourceId==null||sourceId.isBlank()) throw new IllegalArgumentException("Grant source is required");
        transactions.execute(userId,()->{store.revoke(userId,sourceId);return null;});
    }
}
