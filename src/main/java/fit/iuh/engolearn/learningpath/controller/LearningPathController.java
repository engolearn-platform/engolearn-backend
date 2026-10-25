
package fit.iuh.engolearn.learningpath.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import fit.iuh.engolearn.shared.web.ApiResponse;
import fit.iuh.engolearn.learningpath.dto.LearningPathDetailResponse;
import fit.iuh.engolearn.learningpath.dto.LearningPathListResponse;
import fit.iuh.engolearn.learningpath.dto.LearningPathListStatus;
import fit.iuh.engolearn.learningpath.service.LearningPathService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/learning-paths")
public class LearningPathController {

    private static final String OBJECT_ID_PATTERN = "^[a-fA-F0-9]{24}$";

    private final LearningPathService learningPathService;

    public LearningPathController(LearningPathService learningPathService) {
        this.learningPathService = learningPathService;
    }

    @GetMapping
    public ApiResponse<LearningPathListResponse> getLearningPaths(
            @Pattern(regexp = OBJECT_ID_PATTERN, message = "userId không hợp lệ")
            @RequestParam String userId,
            @RequestParam(defaultValue = "ACTIVE") LearningPathListStatus status
    ) {
        return ApiResponse.ok(learningPathService.getByUserId(userId, status));
    }

    @GetMapping("/search")
    public ApiResponse<LearningPathListResponse> searchLearningPaths(
            @Pattern(regexp = OBJECT_ID_PATTERN, message = "userId không hợp lệ")
            @RequestParam String userId,
            @NotBlank(message = "keyword không được để trống")
            @Size(max = 100, message = "keyword không được vượt quá 100 ký tự")
            @RequestParam String keyword
    ) {
        return ApiResponse.ok(learningPathService.search(userId, keyword));
    }

    @GetMapping("/{pathId}")
    public ApiResponse<LearningPathDetailResponse> getLearningPathDetail(
            @Pattern(regexp = OBJECT_ID_PATTERN, message = "pathId không hợp lệ")
            @PathVariable String pathId,
            @Pattern(regexp = OBJECT_ID_PATTERN, message = "userId không hợp lệ")
            @RequestParam String userId
    ) {
        return ApiResponse.ok(learningPathService.getDetail(pathId, userId));
    }
}
