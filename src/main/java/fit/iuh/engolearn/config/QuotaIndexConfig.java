package fit.iuh.engolearn.config;

import fit.iuh.engolearn.quota.repository.QuotaRepository;
import fit.iuh.engolearn.quota.service.QuotaTransactions;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QuotaIndexConfig {
    @Bean
    public InitializingBean quotaIndexes(QuotaRepository repository, QuotaTransactions transactions) {
        return () -> { if (transactions.available()) repository.ensureIndexes(); };
    }
}
