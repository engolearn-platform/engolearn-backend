package fit.iuh.engolearn.assessment.service;
import java.util.function.Supplier;
public interface AssessmentTransactions {
    <T> T execute(String user,Supplier<T> action);
}
