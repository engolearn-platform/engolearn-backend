package fit.iuh.engolearn.placement.controller;

import fit.iuh.engolearn.placement.dto.response.PlacementResultResponse;
import fit.iuh.engolearn.placement.dto.response.PlacementReviewResponse;
import fit.iuh.engolearn.placement.service.PlacementResultService;
import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import fit.iuh.engolearn.shared.web.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/placement/attempts")
public class PlacementResultController {
    private final PlacementResultService results;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/{attemptId}/result")
    public ApiResponse<PlacementResultResponse> getResult(@PathVariable @NotBlank String attemptId,
                                                           HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        String userId = currentUserProvider.getCurrentUser().map(user -> user.id()).orElse(null);
        return ApiResponse.ok(results.getResult(attemptId, userId));
    }

    @GetMapping("/{attemptId}/review")
    public ApiResponse<PlacementReviewResponse> getReview(@PathVariable @NotBlank String attemptId,
                                                           HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        String userId = currentUserProvider.getCurrentUser().map(user -> user.id()).orElse(null);
        return ApiResponse.ok(results.getReview(attemptId, userId));
    }
}
