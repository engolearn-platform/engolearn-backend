package fit.iuh.engolearn.subscription.controller;

import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import fit.iuh.engolearn.shared.web.ApiResponse;
import fit.iuh.engolearn.subscription.dto.request.AdminPublicationRequest;
import fit.iuh.engolearn.subscription.dto.response.AdminConfigurationResponse;
import fit.iuh.engolearn.subscription.dto.response.AdminConfigurationResponse.*;
import fit.iuh.engolearn.subscription.service.AdminConfigurationService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import static fit.iuh.engolearn.subscription.controller.SubscriptionHttpValidation.*;

import org.springframework.web.bind.annotation.*;


@RestController
@lombok.RequiredArgsConstructor
@RequestMapping("/api/v1/subscription/admin")
public class AdminConfigurationController {
    private final AdminConfigurationService service;
    private final CurrentUserProvider users;

    private void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
    }

    @GetMapping("/catalog")
    public ApiResponse<ConfigurationCatalogResponse> catalog(HttpServletResponse response) {
        noStore(response);
        return ApiResponse.ok(service.catalog());
    }

    @GetMapping("/configurations/{scope}")
    public ApiResponse<AdminConfigurationResponse> configuration(@PathVariable String scope, HttpServletResponse response) {
        noStore(response);
        return ApiResponse.ok(service.configuration(scope(scope)));
    }

    @GetMapping("/configurations/{scope}/versions")
    public ApiResponse<ConfigurationHistoryResponse> history(@PathVariable String scope, @RequestParam(defaultValue = "20") String limit,
                                        @RequestParam(required = false) String beforeVersion,
                                        HttpServletResponse response) {
        noStore(response);
        long count = positive(limit);
        if (count > 50) throw invalidAdmin();
        return ApiResponse.ok(
                service.history(scope(scope), (int) count, beforeVersion == null ? null : positive(beforeVersion)));
    }

    @GetMapping("/configurations/{scope}/versions/{version}")
    public ApiResponse<ConfigurationVersionResponse> detail(@PathVariable String scope, @PathVariable String version,
                                       HttpServletResponse response) {
        noStore(response);
        return ApiResponse.ok(service.detail(scope(scope), positive(version)));
    }

    @PostMapping("/configurations/{scope}/preview")
    public ApiResponse<ConfigurationPreviewResponse> preview(@PathVariable String scope, @Valid @RequestBody AdminPublicationRequest body,
                                        HttpServletResponse response) {
        noStore(response);
        return ApiResponse.ok(service.preview(scope(scope), body));
    }

    @PostMapping("/configurations/{scope}/publications")
    public ApiResponse<PublicationResultResponse> publish(@PathVariable String scope,
                                                  @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                  @Valid @RequestBody AdminPublicationRequest body, HttpServletResponse response) {
        noStore(response);
        String normalizedKey = adminKey(key);
        var result =
                service.publish(users.requireCurrentUserId(), normalizedKey, scope(scope), body);
        response.setStatus(result.created() ? 201 : 200);
        return ApiResponse.ok(result.result());
    }
}
