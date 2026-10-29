package fit.iuh.engolearn.quota.controller;

import fit.iuh.engolearn.quota.dto.response.QuotaUsageSummary;
import fit.iuh.engolearn.quota.service.QuotaService;
import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import fit.iuh.engolearn.shared.web.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/quota/me")
public class QuotaController {
    private final QuotaService service;
    private final CurrentUserProvider users;

    public QuotaController(QuotaService service, CurrentUserProvider users) { this.service = service; this.users = users; }

    @GetMapping("/usage")
    public ApiResponse<QuotaUsageSummary> usage(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.ok(service.usage(users.requireCurrentUserId()));
    }
}
