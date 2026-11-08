package fit.iuh.engolearn.config;

import fit.iuh.engolearn.placement.infra.MongoPlacementTransactionExecutor;
import fit.iuh.engolearn.placement.repository.PlacementAttemptRepository;
import fit.iuh.engolearn.placement.service.*;
import fit.iuh.engolearn.placement.service.impl.PlacementLevelSyncClaimServiceImpl;
import fit.iuh.engolearn.placement.service.impl.PlacementLevelSyncDeadlineServiceImpl;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@ConditionalOnProperty(prefix = "engolearn.placement.persistence", name = "mode", havingValue = "mongo")
public class PlacementLevelIntegrationConfig {
    @Bean
    public PlacementTransactionExecutor placementLevelTransactions(MongoTransactionManager manager) {
        return new MongoPlacementTransactionExecutor(new TransactionTemplate(manager));
    }

    // Additive seam: original engines stay intact; only real Mongo wiring gains the joint level transaction.
    @Bean
    @Primary
    public PlacementDeadlineService placementLevelSyncDeadlines(
            @Qualifier("placementDeadlineServiceImpl") PlacementDeadlineService original,
            PlacementTransactionExecutor transactions, PlacementAttemptRepository attempts,
            PlacementUserLevelSynchronizer levels) {
        return new PlacementLevelSyncDeadlineServiceImpl(original, transactions, attempts, levels);
    }

    @Bean
    @Primary
    public PlacementClaimService placementLevelSyncClaims(
            @Qualifier("placementClaimServiceImpl") PlacementClaimService original,
            PlacementTransactionExecutor transactions, PlacementAttemptRepository attempts,
            PlacementUserLevelSynchronizer levels) {
        return new PlacementLevelSyncClaimServiceImpl(original, transactions, attempts, levels);
    }
}
