package fit.iuh.engolearn.placement.controller;

import fit.iuh.engolearn.placement.dto.response.PlacementLearningResponse;
import fit.iuh.engolearn.placement.service.PlacementLearningService;
import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import fit.iuh.engolearn.shared.web.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/placement/attempts")
@ConditionalOnProperty(prefix = "engolearn.placement.persistence", name = "mode", havingValue = "mongo")
public class PlacementLearningController {
    private final PlacementLearningService learning;
    private final CurrentUserProvider users;

    @PostMapping("/{attemptId}/learning")
    public ApiResponse<PlacementLearningResponse> activate(@PathVariable @NotBlank String attemptId,
                                                           HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        var user = users.requireCurrentUser();
        return ApiResponse.ok(learning.activate(attemptId, user.id()));
    }
}
