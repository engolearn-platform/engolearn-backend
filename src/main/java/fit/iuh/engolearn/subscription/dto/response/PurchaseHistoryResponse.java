package fit.iuh.engolearn.subscription.dto.response;
import java.util.List;
public record PurchaseHistoryResponse(List<PurchaseResponse> items, String nextCursor) {
    public PurchaseHistoryResponse { items = List.copyOf(items); }
}
