package fit.iuh.engolearn.topic.controller;

import fit.iuh.engolearn.shared.web.ApiResponse;
import fit.iuh.engolearn.topic.dto.request.GetTopicItemContentRequest;
import fit.iuh.engolearn.topic.dto.request.GetTopicItemPositionRequest;
import fit.iuh.engolearn.topic.dto.request.GetTopicProgressRequest;
import fit.iuh.engolearn.topic.dto.request.SaveTopicItemPositionRequest;
import fit.iuh.engolearn.topic.dto.request.UpdateTopicItemPositionRequest;
import fit.iuh.engolearn.topic.dto.response.TopicItemContentResponse;
import fit.iuh.engolearn.topic.dto.response.TopicItemPositionResponse;
import fit.iuh.engolearn.topic.dto.response.TopicProgressResponse;
import fit.iuh.engolearn.topic.service.TopicLearningService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class TopicLearningController {

    private final TopicLearningService topicLearningService;
    private final fit.iuh.engolearn.topic.service.TopicAttemptService attempts;

    public TopicLearningController(TopicLearningService topicLearningService, fit.iuh.engolearn.topic.service.TopicAttemptService attempts) {
        this.topicLearningService = topicLearningService;
        this.attempts = attempts;
    }

    @GetMapping("/topic-items/{itemId}/content")
    public ApiResponse<TopicItemContentResponse> getItemContent(
            @Valid @NotBlank @PathVariable("itemId") String itemId) {
        throw new fit.iuh.engolearn.topic.exception.TopicRequestException(410, "INVALID_REQUEST", "Use the owned attempt content endpoint");
    }

    @GetMapping("/topic-items/{itemId}/position")
    public ApiResponse<TopicItemPositionResponse> getItemPosition(
            @Valid @NotBlank @PathVariable("itemId") String itemId) {
        return ApiResponse.ok(topicLearningService.getItemPosition(
                GetTopicItemPositionRequest.builder().topicItemId(itemId).build()));
    }

    @PutMapping("/topic-items/{itemId}/position")
    public ApiResponse<TopicItemPositionResponse> saveItemPosition(
            @Valid @NotBlank @PathVariable("itemId") String itemId,
            @Valid @NotNull @RequestBody UpdateTopicItemPositionRequest body) {
        SaveTopicItemPositionRequest request = SaveTopicItemPositionRequest.builder()
                .topicItemId(itemId)
                .stage(body == null ? null : body.getStage())
                .itemIndex(body == null ? null : body.getItemIndex())
                .build();
        throw new fit.iuh.engolearn.topic.exception.TopicRequestException(410, "INVALID_REQUEST", "Use attempt start/advance; arbitrary position writes are retired");
    }

    @GetMapping("/topics/{topicId}/progress")
    public ApiResponse<TopicProgressResponse> getTopicProgress(
            @Valid @NotBlank @PathVariable("topicId") String topicId) {
        return ApiResponse.ok(attempts.topicProgress(topicId));
    }
}
