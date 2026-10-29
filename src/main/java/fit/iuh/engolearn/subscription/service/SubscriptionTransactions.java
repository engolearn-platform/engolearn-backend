package fit.iuh.engolearn.subscription.service;

import fit.iuh.engolearn.subscription.contract.SubscriptionUnavailableException;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class SubscriptionTransactions {
    private final ObjectProvider<MongoTransactionManager> managers;
    public SubscriptionTransactions(ObjectProvider<MongoTransactionManager> managers) { this.managers=managers; }
    public <T> T execute(Supplier<T> action) {
        MongoTransactionManager manager=managers.getIfAvailable();
        if(manager==null) throw new SubscriptionUnavailableException();
        return new TransactionTemplate(manager).execute(status -> action.get());
    }
    public void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }
}
