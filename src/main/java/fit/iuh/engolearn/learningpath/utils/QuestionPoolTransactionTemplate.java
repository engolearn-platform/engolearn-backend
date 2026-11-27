package fit.iuh.engolearn.learningpath.utils;

import java.util.Collection;
import java.util.Set;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.transaction.support.TransactionTemplate;

public class QuestionPoolTransactionTemplate extends TransactionTemplate implements TransactionAttribute {
    public QuestionPoolTransactionTemplate(PlatformTransactionManager manager) {
        super(manager);
    }

    // Mongo resolves labels from the definition passed by TransactionTemplate itself.
    @Override
    public Collection<String> getLabels() {
        return Set.of("mongo:readConcern=SNAPSHOT", "mongo:writeConcern=MAJORITY");
    }

    @Override
    public String getQualifier() { return null; }

    @Override
    public boolean rollbackOn(Throwable failure) { return true; }
}
