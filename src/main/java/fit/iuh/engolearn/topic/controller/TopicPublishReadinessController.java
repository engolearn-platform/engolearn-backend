package fit.iuh.engolearn.topic.controller;

import fit.iuh.engolearn.shared.web.ApiResponse;
import fit.iuh.engolearn.topic.dto.response.TopicPublishReadinessResponse;
import fit.iuh.engolearn.topic.service.TopicPublishReadinessService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/management/topics")
@RequiredArgsConstructor
public class TopicPublishReadinessController {
    private final TopicPublishReadinessService service;

    @GetMapping("/{topicId}/readiness")
    public ApiResponse<TopicPublishReadinessResponse> readiness(@PathVariable String topicId) {
        return ApiResponse.ok(service.readiness(topicId));
    }
}
