package fit.iuh.engolearn.subscription.service;
import fit.iuh.engolearn.subscription.dto.request.CreatePurchaseRequest;
import fit.iuh.engolearn.subscription.dto.response.PurchaseResponse;
public interface PurchaseService {
    Creation create(String userId, String key, CreatePurchaseRequest request);
    PurchaseResponse detail(String userId, String reference);
    record Creation(PurchaseResponse purchase, boolean created) {}
}
