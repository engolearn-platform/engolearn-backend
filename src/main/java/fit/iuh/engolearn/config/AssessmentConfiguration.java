package fit.iuh.engolearn.config;

import fit.iuh.engolearn.assessment.contract.AssessmentBaselineSource;
import fit.iuh.engolearn.assessment.contract.dto.AssessmentBaselineDto;
import fit.iuh.engolearn.assessment.infra.dev.AssessmentDevBaselineSource;
import fit.iuh.engolearn.placement.contract.PlacementBaselineLookup;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AssessmentConfiguration {
    @Bean
    @Primary
    public AssessmentBaselineSource assessmentBaselineSource(
            PlacementBaselineLookup placement, ObjectProvider<AssessmentDevBaselineSource> dev) {
        return user -> placement.findForUser(user)
                .map(result -> new AssessmentBaselineDto(result.resultId(), result.level().name()))
                .or(() -> {
                    var fixture = dev.getIfAvailable();
                    return fixture == null ? Optional.empty() : fixture.findValidBaseline(user);
                });
    }
}
