package fit.iuh.engolearn.config;
import fit.iuh.engolearn.assessment.contract.AssessmentBaselineSource;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;

@Configuration
public class AssessmentConfiguration {
    @Bean
    @Profile("!dev | prod")
    @ConditionalOnMissingBean(AssessmentBaselineSource.class)
    public AssessmentBaselineSource assessmentBaselineSource() {return user->Optional.empty();}
}
