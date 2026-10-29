package fit.iuh.engolearn.topic.controller;

import fit.iuh.engolearn.shared.web.ApiResponse;
import fit.iuh.engolearn.topic.dto.request.StartTopicRetryRequest;
import fit.iuh.engolearn.topic.dto.response.TopicRetrySessionResponse;
import fit.iuh.engolearn.topic.service.TopicRetryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class TopicRetryController {
    private final TopicRetryService retries;
    public TopicRetryController(TopicRetryService retries) { this.retries=retries; }

    @PostMapping("/topics/{topicId}/retry-sessions")
    public ApiResponse<TopicRetrySessionResponse> start(@PathVariable String topicId,@Valid @RequestBody StartTopicRetryRequest body,
            @NotBlank @Size(max=128) @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.ok(retries.startTopic(topicId,body,key));
    }

    @GetMapping("/topic-retry-sessions/{retrySessionId}")
    public ApiResponse<TopicRetrySessionResponse> get(@PathVariable String retrySessionId) {
        return ApiResponse.ok(retries.getSession(retrySessionId));
    }
}
