package fit.iuh.engolearn.topic.controller;

import fit.iuh.engolearn.shared.web.ApiResponse;
import fit.iuh.engolearn.topic.dto.request.ResetTopicRequest;
import fit.iuh.engolearn.topic.dto.response.ResetTopicResponse;
import fit.iuh.engolearn.topic.service.TopicResetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class TopicResetController {
    private final TopicResetService resets;
    public TopicResetController(TopicResetService resets) { this.resets = resets; }
    @PostMapping("/topics/{topicId}/reset")
    public ApiResponse<ResetTopicResponse> reset(@PathVariable String topicId, @Valid @RequestBody ResetTopicRequest body,
            @NotBlank @Size(max = 128) @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.ok(resets.reset(topicId, body, key));
    }
}
