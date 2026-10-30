package fit.iuh.engolearn.quota.service;
import fit.iuh.engolearn.subscription.contract.event.QuotaPolicyActivatedEvent;
public interface QuotaActivationService { void onPolicyActivated(QuotaPolicyActivatedEvent activation); }
