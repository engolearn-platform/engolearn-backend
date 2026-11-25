package fit.iuh.engolearn.placement.controller;

import fit.iuh.engolearn.placement.dto.request.PlacementAdminAttemptSearchRequest;
import fit.iuh.engolearn.placement.dto.response.PlacementAdminAttemptDetailResponse;
import fit.iuh.engolearn.placement.dto.response.PlacementAdminAttemptPageResponse;
import fit.iuh.engolearn.placement.service.PlacementAdminAttemptService;
import fit.iuh.engolearn.shared.web.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/placement/admin/attempts")
public class PlacementAdminAttemptController {
    private final PlacementAdminAttemptService attempts;

    @GetMapping
    public ApiResponse<PlacementAdminAttemptPageResponse> list(
            @Valid @ModelAttribute PlacementAdminAttemptSearchRequest request, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return ApiResponse.ok(attempts.search(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<PlacementAdminAttemptDetailResponse> detail(
            @PathVariable @NotBlank @Size(max = 128) String id, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return ApiResponse.ok(attempts.getDetail(id));
    }
}
