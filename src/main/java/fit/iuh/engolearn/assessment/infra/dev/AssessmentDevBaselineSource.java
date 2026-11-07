package fit.iuh.engolearn.assessment.infra.dev;

import fit.iuh.engolearn.assessment.contract.AssessmentBaselineSource;
import fit.iuh.engolearn.assessment.contract.dto.AssessmentBaselineDto;
import fit.iuh.engolearn.assessment.repository.AssessmentRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@lombok.RequiredArgsConstructor
@Profile("dev & !prod")
public class AssessmentDevBaselineSource implements AssessmentBaselineSource {
    private final AssessmentRepository store;
    public Optional<AssessmentBaselineDto> findValidBaseline(String userId) {
        var d=store.devBaseline(userId);
        if(d==null||!Boolean.TRUE.equals(d.valid())||!List.of("A1","A2","B1","B2","C1","C2").contains(d.level())) return Optional.empty();
        return Optional.of(new AssessmentBaselineDto("DEV_FIXTURE:"+userId,d.level()));
    }
}
