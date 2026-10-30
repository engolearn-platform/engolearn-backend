package fit.iuh.engolearn.quota.service;
import java.util.function.Supplier;
public interface QuotaTransactions {
    boolean available();
    <T> T execute(Supplier<T> action);
    <T> T execute(Supplier<T> action, Supplier<T> acceptedLookup);
    <T> T event(Supplier<T> action);
}
