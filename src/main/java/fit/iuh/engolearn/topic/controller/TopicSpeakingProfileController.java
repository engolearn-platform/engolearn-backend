package fit.iuh.engolearn.topic.controller;

import fit.iuh.engolearn.shared.web.ApiResponse;
import fit.iuh.engolearn.topic.contract.dto.SpeakingScopeDto;
import fit.iuh.engolearn.topic.contract.enums.SpeakingScopeType;
import fit.iuh.engolearn.topic.dto.request.TopicSpeakingProfileRequest;
import fit.iuh.engolearn.topic.dto.response.TopicSpeakingProfileResponse;
import fit.iuh.engolearn.topic.service.TopicSpeakingProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/management/topics/{topicId}")
@RequiredArgsConstructor
public class TopicSpeakingProfileController {
    private final TopicSpeakingProfileService profiles;

    @GetMapping({"/speaking-profile", "/items/{itemId}/speaking-profile"})
    public ApiResponse<TopicSpeakingProfileResponse> read(@PathVariable String topicId, @PathVariable(required = false) String itemId) {
        return ApiResponse.ok(profiles.read(scope(topicId, itemId)));
    }

    @PutMapping({"/speaking-profile", "/items/{itemId}/speaking-profile"})
    public ApiResponse<TopicSpeakingProfileResponse> save(@PathVariable String topicId, @PathVariable(required = false) String itemId,
                                                          @Valid @RequestBody TopicSpeakingProfileRequest request) {
        return ApiResponse.ok(profiles.save(scope(topicId, itemId), request.revision(), request.profile()));
    }

    @PostMapping({"/speaking-profile/publish", "/items/{itemId}/speaking-profile/publish"})
    public ApiResponse<TopicSpeakingProfileResponse> publish(@PathVariable String topicId, @PathVariable(required = false) String itemId,
                                                             @Valid @RequestBody TopicSpeakingProfileRequest.Publish request) {
        return ApiResponse.ok(profiles.publish(scope(topicId, itemId), request.revision()));
    }

    private SpeakingScopeDto scope(String topicId, String itemId) {
        return new SpeakingScopeDto(itemId == null ? SpeakingScopeType.TOPIC : SpeakingScopeType.TOPIC_ITEM, topicId, itemId);
    }
}
