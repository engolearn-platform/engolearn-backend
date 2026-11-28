package fit.iuh.engolearn.managementdashboard.controller;

import fit.iuh.engolearn.managementdashboard.dto.response.DashboardSummaryResponse;
import fit.iuh.engolearn.managementdashboard.service.DashboardSummaryService;
import fit.iuh.engolearn.shared.web.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/management/dashboard")
@RequiredArgsConstructor
public class DashboardSummaryController {
    private final DashboardSummaryService service;
    private final Clock clock;

    @GetMapping("/summary")
    public ApiResponse<DashboardSummaryResponse> summary(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return new ApiResponse<>(service.summary(), clock.instant());
    }
}
