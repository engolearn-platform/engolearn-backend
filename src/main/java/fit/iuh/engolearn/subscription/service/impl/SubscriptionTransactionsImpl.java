package fit.iuh.engolearn.subscription.service.impl;

import fit.iuh.engolearn.subscription.service.*;
import lombok.RequiredArgsConstructor;

import fit.iuh.engolearn.subscription.contract.exception.SubscriptionUnavailableException;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
public class SubscriptionTransactionsImpl implements SubscriptionTransactions {
    private final ObjectProvider<MongoTransactionManager> managers;
    public <T> T execute(Supplier<T> action) {
        MongoTransactionManager manager=managers.getIfAvailable();
        if(manager==null) throw new SubscriptionUnavailableException();
        return new TransactionTemplate(manager).execute(status -> action.get());
    }
    public <T> T independent(Supplier<T> action) {
        MongoTransactionManager manager = managers.getIfAvailable();
        if (manager == null) throw new SubscriptionUnavailableException();
        TransactionTemplate transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction.execute(status -> action.get());
    }
    public void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }
}
