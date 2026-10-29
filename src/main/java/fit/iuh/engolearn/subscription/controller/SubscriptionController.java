package fit.iuh.engolearn.subscription.controller;
import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import fit.iuh.engolearn.shared.web.ApiResponse;
import fit.iuh.engolearn.subscription.dto.response.*;
import fit.iuh.engolearn.subscription.exception.SubscriptionRequestException;
import fit.iuh.engolearn.subscription.service.SubscriptionService;
import jakarta.servlet.http.HttpServletResponse;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/subscription")
public class SubscriptionController {
    private final SubscriptionService service;private final CurrentUserProvider users;
    public SubscriptionController(SubscriptionService service,CurrentUserProvider users) {this.service=service;this.users=users;}
    public record PlanList(List<PlanCard> items) {}
    @GetMapping("/plans")
    public ApiResponse<PlanList> plans(HttpServletResponse response) {response.setHeader("Cache-Control","no-store");return ApiResponse.ok(new PlanList(service.plans()));}
    @GetMapping("/me")
    public ApiResponse<MySubscriptionSummary> me(HttpServletResponse response) {response.setHeader("Cache-Control","no-store");return ApiResponse.ok(service.summary(users.requireCurrentUserId()));}
    @PostMapping("/me/trial")
    public ApiResponse<TrialActivation> trial(@RequestHeader(value="Idempotency-Key",required=false) String key,@RequestBody(required=false) Map<String,Object> body,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        try {if(key==null || !UUID.fromString(key).toString().equalsIgnoreCase(key) || body==null || !body.isEmpty()) throw new IllegalArgumentException();}
        catch(IllegalArgumentException ex) {throw new SubscriptionRequestException(400,"INVALID_REQUEST","An empty JSON object and a UUID Idempotency-Key are required");}
        var result=service.activateTrial(users.requireCurrentUserId(),key);
        response.setStatus(result.created()?201:200);return ApiResponse.ok(result.activation());
    }
}
