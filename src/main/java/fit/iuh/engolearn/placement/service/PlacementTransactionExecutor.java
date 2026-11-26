package fit.iuh.engolearn.placement.service;

import java.util.function.Supplier;

public interface PlacementTransactionExecutor {
    <T> T execute(Supplier<T> action);
}
