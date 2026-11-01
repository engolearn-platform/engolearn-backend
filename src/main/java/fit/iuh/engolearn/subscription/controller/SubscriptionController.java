package fit.iuh.engolearn.subscription.controller;

import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import fit.iuh.engolearn.shared.web.ApiResponse;
import fit.iuh.engolearn.subscription.dto.response.*;
import fit.iuh.engolearn.subscription.service.SubscriptionService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import fit.iuh.engolearn.subscription.dto.request.TrialActivationRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@lombok.RequiredArgsConstructor
@RequestMapping("/api/v1/subscription")
public class SubscriptionController {
    private final SubscriptionService service;
    private final CurrentUserProvider users;
    private final fit.iuh.engolearn.subscription.service.PurchaseService purchases;

    @GetMapping("/plans")
    public ApiResponse<PlanListResponse> plans(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.ok(new PlanListResponse(service.plans()));
    }

    @GetMapping("/me")
    public ApiResponse<MySubscriptionSummaryResponse> me(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.ok(service.summary(users.requireCurrentUserId()));
    }

    @PostMapping("/me/purchases")
    public ApiResponse<PurchaseResponse> createPurchase(
            @RequestHeader(value="Idempotency-Key", required=false) String key,
            @Valid @RequestBody fit.iuh.engolearn.subscription.dto.request.CreatePurchaseRequest body,
            jakarta.servlet.http.HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        var result = purchases.create(users.requireCurrentUserId(), key, body, request.getRemoteAddr());
        response.setStatus(result.created() ? 201 : 200);
        return ApiResponse.ok(result.purchase());
    }

    @GetMapping("/me/purchases")
    public ApiResponse<PurchaseHistoryResponse> history(@RequestParam(defaultValue="20") String limit,
            @RequestParam(required=false) String cursor, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        int pageSize;
        try { pageSize = Integer.parseInt(limit); }
        catch (NumberFormatException invalid) {
            throw new fit.iuh.engolearn.subscription.exception.SubscriptionRequestException(
                    400, "INVALID_REQUEST", "History limit must be an integer between 1 and 50");
        }
        return ApiResponse.ok(purchases.history(users.requireCurrentUserId(), pageSize, cursor));
    }

    @GetMapping("/me/purchases/{reference}")
    public ApiResponse<PurchaseResponse> purchase(@PathVariable String reference, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.ok(purchases.detail(users.requireCurrentUserId(), reference));
    }

    @PostMapping("/me/trial")
    public ApiResponse<TrialActivationResponse> trial(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody TrialActivationRequest body, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        SubscriptionHttpValidation.trialKey(key);
        var result = service.activateTrial(users.requireCurrentUserId(), key);
        response.setStatus(result.created() ? 201 : 200);
        return ApiResponse.ok(result.activation());
    }
}
