package fit.iuh.engolearn.subscription.contract;

public interface SubscriptionAccountInitialization {
    /**
     * Enrolls a newly registered user in the caller's Mongo transaction.
     * Repeated calls preserve existing subscription history and Trial eligibility.
     */
    void initializeNewAccount(String userId);
}
