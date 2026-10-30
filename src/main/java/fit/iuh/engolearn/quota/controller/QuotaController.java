package fit.iuh.engolearn.quota.controller;

import fit.iuh.engolearn.quota.dto.response.QuotaUsageSummaryResponse;
import fit.iuh.engolearn.quota.service.QuotaService;
import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import fit.iuh.engolearn.shared.web.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@lombok.RequiredArgsConstructor
@RequestMapping("/api/v1/quota/me")
public class QuotaController {
    private final QuotaService service;
    private final CurrentUserProvider users;


    @GetMapping("/usage")
    public ApiResponse<QuotaUsageSummaryResponse> usage(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.ok(service.usage(users.requireCurrentUserId()));
    }
}
