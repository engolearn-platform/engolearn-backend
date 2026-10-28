package fit.iuh.engolearn.topic.controller;

import fit.iuh.engolearn.shared.web.ApiResponse;
import fit.iuh.engolearn.topic.dto.response.TopicViewerResponse;
import fit.iuh.engolearn.topic.service.TopicViewerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/topic-learning")
public class TopicViewerController {
    private final TopicViewerService viewerService;

    public TopicViewerController(TopicViewerService viewerService) {
        this.viewerService = viewerService;
    }

    @GetMapping("/viewer")
    public ApiResponse<TopicViewerResponse> getViewer() {
        return ApiResponse.ok(viewerService.getViewer());
    }
}
