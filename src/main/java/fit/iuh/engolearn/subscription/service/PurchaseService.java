package fit.iuh.engolearn.subscription.service;
import fit.iuh.engolearn.subscription.dto.request.CreatePurchaseRequest;
import fit.iuh.engolearn.subscription.dto.response.PurchaseResponse;
import fit.iuh.engolearn.subscription.dto.response.PurchaseHistoryResponse;
public interface PurchaseService {
    Creation create(String userId, String key, CreatePurchaseRequest request);
    Creation create(String userId, String key, CreatePurchaseRequest request, String clientIp);
    PurchaseResponse detail(String userId, String reference);
    PurchaseHistoryResponse history(String userId, int limit, String cursor);
    record Creation(PurchaseResponse purchase, boolean created) {}
}
