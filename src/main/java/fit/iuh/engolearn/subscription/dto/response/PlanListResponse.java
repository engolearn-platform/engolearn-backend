package fit.iuh.engolearn.subscription.dto.response;
import java.util.List;
public record PlanListResponse(List<PlanCardResponse> items) {
    public PlanListResponse { items = List.copyOf(items); }
}
