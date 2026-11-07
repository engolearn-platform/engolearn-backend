package fit.iuh.engolearn.assessment.service.impl;
import fit.iuh.engolearn.assessment.service.*;

import com.mongodb.MongoException;
import fit.iuh.engolearn.assessment.exception.AssessmentException;
import fit.iuh.engolearn.assessment.repository.AssessmentRepository;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@lombok.RequiredArgsConstructor
public class AssessmentTransactionsImpl implements AssessmentTransactions {
    private final ObjectProvider<MongoTransactionManager> managers;
    private final AssessmentRepository store;
    public <T> T execute(String user,Supplier<T> action) {
        var manager=managers.getIfAvailable();
        if(manager==null) throw new AssessmentException(503,"TRANSACTIONS_REQUIRED","MongoDB replica set transactions are required");
        store.ensureGate(user);
        for(int i=0;i<6;i++) {
            try {return new TransactionTemplate(manager).execute(status->{store.lock(user);return action.get();});}
            catch(RuntimeException e) {
                if(!retryable(e)) throw e;
                if(i==5) throw new AssessmentException(503,"ASSESSMENT_BUSY","Please retry the same request",e);
                try {Thread.sleep(10L << i);}
                catch(InterruptedException interrupted) {Thread.currentThread().interrupt();throw new AssessmentException(503,"ASSESSMENT_BUSY","Please retry the same request");}
            }
        }
        throw new IllegalStateException();
    }
    private boolean retryable(Throwable e) {
        for(Throwable c=e;c!=null;c=c.getCause()) {
            if(c instanceof DuplicateKeyException || c instanceof OptimisticLockingFailureException) return true;
            if(c instanceof MongoException m && (m.hasErrorLabel("TransientTransactionError") || m.hasErrorLabel("UnknownTransactionCommitResult") || m.getCode()==112)) return true;
        }
        return false;
    }
}
