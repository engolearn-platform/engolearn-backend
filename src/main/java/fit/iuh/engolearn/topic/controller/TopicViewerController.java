package fit.iuh.engolearn.topic.controller;

import lombok.RequiredArgsConstructor;
import fit.iuh.engolearn.shared.security.impl.AuthenticatedUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;

import fit.iuh.engolearn.shared.web.ApiResponse;
import fit.iuh.engolearn.topic.dto.response.TopicViewerResponse;
import fit.iuh.engolearn.topic.service.TopicViewerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@PreAuthorize("hasAnyRole('USER', 'CONTENT_MANAGER', 'ADMIN')")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/topic-learning")
public class TopicViewerController {
    private final TopicViewerService viewerService;

    @GetMapping("/viewer")
    public ApiResponse<TopicViewerResponse> getViewer(@AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return ApiResponse.ok(viewerService.getViewer(principal.getId()));
    }
}
