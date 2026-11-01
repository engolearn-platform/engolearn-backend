package fit.iuh.engolearn.subscription.service;

import java.util.function.Supplier;

public interface SubscriptionTransactions {
    <T> T execute(Supplier<T> action);
    <T> T independent(Supplier<T> action);
    void afterCommit(Runnable action);
}
