package fit.iuh.engolearn.subscription.controller;
import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import fit.iuh.engolearn.shared.web.ApiResponse;
import fit.iuh.engolearn.subscription.dto.request.RefundRequest;
import fit.iuh.engolearn.subscription.dto.response.RefundResponse;
import fit.iuh.engolearn.subscription.service.SubscriptionRefundService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/subscription/admin/purchases")
public class AdminRefundController {
    private final SubscriptionRefundService refunds;
    private final CurrentUserProvider users;
    @PostMapping("/{reference}/refund")
    public ApiResponse<RefundResponse> refund(@PathVariable String reference,
            @RequestHeader(value="Idempotency-Key",required=false) String key,
            @Valid @RequestBody RefundRequest body, HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        var result=refunds.request(users.requireCurrentUserId(),reference,key,body.reason());
        response.setStatus(result.created()?202:200);
        return ApiResponse.ok(result.refund());
    }
}
