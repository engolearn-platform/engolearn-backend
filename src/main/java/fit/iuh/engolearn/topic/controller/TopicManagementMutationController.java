package fit.iuh.engolearn.topic.controller;

import fit.iuh.engolearn.shared.web.ApiResponse;
import fit.iuh.engolearn.topic.dto.response.TopicManagementResponse;
import fit.iuh.engolearn.topic.service.TopicManagementMutationService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/management/topics")
@RequiredArgsConstructor
public class TopicManagementMutationController {
    private final TopicManagementMutationService mutations;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TopicManagementResponse> create(@RequestBody Map<String, Object> body,
                                                        HttpServletResponse response) {
        TopicManagementResponse created = mutations.create(body);
        response.setHeader("Location", "/api/management/topics/" + created.id());
        return ApiResponse.ok(created);
    }

    @PatchMapping("/{topicId}")
    public ApiResponse<TopicManagementResponse> update(@PathVariable String topicId,
                                                        @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(mutations.update(topicId, body));
    }
}
