package fit.iuh.engolearn.shared.messaging;

import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
public class MongoMessageTransactions {
    private final ObjectProvider<MongoTransactionManager> managers;
    public boolean available() { return managers.getIfAvailable() != null; }
    public <T> T required(Supplier<T> action) {
        MongoTransactionManager manager=managers.getIfAvailable();
        if (manager==null) throw new IllegalStateException("Durable messaging requires Mongo transactions");
        return new TransactionTemplate(manager).execute(status->action.get());
    }
    public <T> T independent(Supplier<T> action) {
        MongoTransactionManager manager = managers.getIfAvailable();
        if (manager == null) throw new IllegalStateException("Durable messaging requires Mongo transactions");
        TransactionTemplate transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction.execute(status -> action.get());
    }
}
