package fit.iuh.engolearn.subscription.controller;
import fit.iuh.engolearn.shared.web.ApiResponse;
import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import fit.iuh.engolearn.subscription.dto.response.DevPurchaseResetResponse;
import fit.iuh.engolearn.subscription.service.DevPurchaseResetService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @Profile("dev & !prod")
@ConditionalOnProperty(name="engolearn.payment.dev-reset.enabled",havingValue="true")
@RequestMapping("/api/v1/subscription/me/purchases")
public class DevPurchaseResetController {
    private final DevPurchaseResetService service;
    private final CurrentUserProvider users;
    @PostMapping("/{reference}/dev-reset")
    public ApiResponse<DevPurchaseResetResponse> reset(@PathVariable String reference,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");return ApiResponse.ok(service.reset(users.requireCurrentUserId(),reference));
    }
}
