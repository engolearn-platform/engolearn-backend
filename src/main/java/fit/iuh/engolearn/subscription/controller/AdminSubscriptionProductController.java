package fit.iuh.engolearn.subscription.controller;

import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import fit.iuh.engolearn.shared.web.ApiResponse;
import fit.iuh.engolearn.subscription.dto.request.*;
import fit.iuh.engolearn.subscription.dto.response.*;
import fit.iuh.engolearn.subscription.service.SubscriptionProductService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/subscription/admin/products")
public class AdminSubscriptionProductController {
    private final SubscriptionProductService products;
    private final CurrentUserProvider users;

    private void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
    }

    private String adminKey(String value) {
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
            return id.toString();
        } catch (RuntimeException invalid) {
            throw new fit.iuh.engolearn.subscription.exception.SubscriptionRequestException(
                    400, "INVALID_REQUEST", "A UUID Idempotency-Key is required");
        }
    }

    @GetMapping
    public ApiResponse<SubscriptionProductAdminListResponse> list(HttpServletResponse response) {
        noStore(response);
        return ApiResponse.ok(products.adminList());
    }

    @PostMapping
    public ApiResponse<SubscriptionProductDetailResponse> create(@Valid @RequestBody CreateSubscriptionProductRequest body,
            HttpServletResponse response) {
        noStore(response);
        response.setStatus(HttpStatus.CREATED.value());
        return ApiResponse.ok(products.create(body.planCode()));
    }

    @GetMapping("/{planCode}")
    public ApiResponse<SubscriptionProductDetailResponse> detail(@PathVariable String planCode,
            HttpServletResponse response) {
        noStore(response);
        return ApiResponse.ok(products.adminDetail(planCode));
    }

    @GetMapping("/{planCode}/versions/{version}")
    public ApiResponse<SubscriptionProductVersionResponse> version(@PathVariable String planCode,
            @PathVariable String version, HttpServletResponse response) {
        noStore(response);
        return ApiResponse.ok(products.versionDetail(planCode, SubscriptionHttpValidation.positive(version)));
    }

    @PutMapping("/{planCode}/draft")
    public ApiResponse<SubscriptionProductDetailResponse> saveDraft(@PathVariable String planCode,
            @Valid @RequestBody SaveSubscriptionProductDraftRequest body, HttpServletResponse response) {
        noStore(response);
        return ApiResponse.ok(products.saveDraft(planCode, body));
    }

    @PostMapping("/{planCode}/preview")
    public ApiResponse<SubscriptionProductPreviewResponse> preview(@PathVariable String planCode,
            @Valid @RequestBody PreviewSubscriptionProductRequest body, HttpServletResponse response) {
        noStore(response);
        return ApiResponse.ok(products.preview(planCode, body));
    }

    @PostMapping("/{planCode}/publish")
    public ApiResponse<SubscriptionProductDetailResponse> publish(@PathVariable String planCode,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody PublishSubscriptionProductRequest body, HttpServletResponse response) {
        noStore(response);
        var result = products.publish(users.requireCurrentUserId(), adminKey(key), planCode, body);
        response.setStatus(HttpStatus.CREATED.value());
        return ApiResponse.ok(result);
    }

    @PatchMapping("/PREMIUM/schedule")
    public ApiResponse<SubscriptionProductDetailResponse> rescheduleLegacyPremium(
            @Valid @RequestBody RescheduleLegacyPremiumRequest body, HttpServletResponse response) {
        noStore(response);
        return ApiResponse.ok(products.rescheduleLegacyPremium(body));
    }

    @PostMapping("/PREMIUM/schedule/cancel")
    public ApiResponse<SubscriptionProductDetailResponse> cancelLegacyPremiumSchedule(
            @Valid @RequestBody CancelLegacyPremiumScheduleRequest body, HttpServletResponse response) {
        noStore(response);
        return ApiResponse.ok(products.cancelLegacyPremiumSchedule(body));
    }

    @PatchMapping("/{planCode}/visibility")
    public ApiResponse<SubscriptionProductDetailResponse> visibility(@PathVariable String planCode,
            @Valid @RequestBody SetProductVisibilityRequest body, HttpServletResponse response) {
        noStore(response);
        return ApiResponse.ok(products.visibility(planCode, body));
    }

    @PatchMapping("/{planCode}/sale")
    public ApiResponse<SubscriptionProductDetailResponse> sale(@PathVariable String planCode,
            @Valid @RequestBody SetProductSaleStatusRequest body, HttpServletResponse response) {
        noStore(response);
        return ApiResponse.ok(products.saleStatus(planCode, body));
    }

    @PostMapping("/{planCode}/archive")
    public ApiResponse<SubscriptionProductDetailResponse> archive(@PathVariable String planCode,
            HttpServletResponse response) {
        noStore(response);
        return ApiResponse.ok(products.archive(planCode));
    }

    @DeleteMapping("/{planCode}/draft")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> deleteDraft(@PathVariable String planCode, HttpServletResponse response) {
        noStore(response);
        products.deleteDraft(planCode);
        return ApiResponse.ok(null);
    }
}
