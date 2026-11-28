package fit.iuh.engolearn.subscription.service.impl;

import fit.iuh.engolearn.subscription.contract.exception.SubscriptionUnavailableException;
import fit.iuh.engolearn.subscription.model.SubscriptionPeriod;
import java.time.Instant;
import java.util.List;

final class EffectivePeriodPolicy {
    private EffectivePeriodPolicy() { }

    static SubscriptionPeriod active(List<SubscriptionPeriod> periods, Instant at) {
        List<SubscriptionPeriod> active = periods.stream().filter(period -> period.effectiveAt(at)).toList();
        if (active.size() > 1) throw new SubscriptionUnavailableException();
        return active.isEmpty() ? null : active.getFirst();
    }
}
