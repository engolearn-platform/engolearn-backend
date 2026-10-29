
package fit.iuh.engolearn.learningpath.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import fit.iuh.engolearn.shared.web.ApiResponse;
import fit.iuh.engolearn.learningpath.dto.response.LearningPathDetailResponse;
import fit.iuh.engolearn.learningpath.dto.response.LearningPathListResponse;
import fit.iuh.engolearn.learningpath.dto.request.LearningPathListStatus;
import fit.iuh.engolearn.learningpath.dto.request.CreateLearningPathRequest;
import fit.iuh.engolearn.learningpath.dto.request.SaveLearningPathAnswerDraftRequest;
import fit.iuh.engolearn.learningpath.dto.response.CreateLearningPathResponse;
import fit.iuh.engolearn.learningpath.dto.response.LearningPathRecommendationResponse;
import fit.iuh.engolearn.learningpath.dto.response.SaveLearningPathAnswerDraftResponse;
import fit.iuh.engolearn.learningpath.dto.response.StartLearningPathDayResponse;
import fit.iuh.engolearn.learningpath.service.LearningPathCreationService;
import fit.iuh.engolearn.learningpath.service.LearningPathAnswerDraftService;
import fit.iuh.engolearn.learningpath.service.LearningPathDayPracticeService;
import fit.iuh.engolearn.learningpath.service.LearningPathRecommendationService;
import fit.iuh.engolearn.learningpath.service.LearningPathService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/learning-paths")
public class LearningPathController {

    private static final String OBJECT_ID_PATTERN = "^[a-fA-F0-9]{24}$";

    private final LearningPathService learningPathService;
    private final LearningPathRecommendationService recommendationService;
    private final LearningPathCreationService creationService;
    private final LearningPathDayPracticeService dayPracticeService;
    private final LearningPathAnswerDraftService answerDraftService;

    public LearningPathController(LearningPathService learningPathService,
                                  LearningPathRecommendationService recommendationService,
                                  LearningPathCreationService creationService,
                                  LearningPathDayPracticeService dayPracticeService,
                                  LearningPathAnswerDraftService answerDraftService) {
        this.learningPathService = learningPathService;
        this.recommendationService = recommendationService;
        this.creationService = creationService;
        this.dayPracticeService = dayPracticeService;
        this.answerDraftService = answerDraftService;
    }

    @GetMapping("/recommendation")
    public ApiResponse<LearningPathRecommendationResponse> getRecommendation(
            @Pattern(regexp = OBJECT_ID_PATTERN, message = "userId không hợp lệ")
            @RequestParam String userId,
            @Pattern(regexp = OBJECT_ID_PATTERN, message = "attemptId không hợp lệ")
            @RequestParam String attemptId
    ) {
        return ApiResponse.ok(recommendationService.recommend(userId, attemptId));
    }

    @PostMapping
    public ApiResponse<CreateLearningPathResponse> createLearningPath(
            @Valid @RequestBody CreateLearningPathRequest request
    ) {
        return ApiResponse.ok(creationService.create(request));
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

    @PostMapping("/{pathId}/weeks/{weekNo}/days/{dayNo}/start")
    public ApiResponse<StartLearningPathDayResponse> startDay(
            @Pattern(regexp = OBJECT_ID_PATTERN, message = "pathId không hợp lệ")
            @PathVariable String pathId,
            @Min(1) @Max(3) @PathVariable int weekNo,
            @Min(1) @Max(5) @PathVariable int dayNo,
            @Pattern(regexp = OBJECT_ID_PATTERN, message = "userId không hợp lệ")
            @RequestParam String userId
    ) {
        return ApiResponse.ok(dayPracticeService.start(pathId, weekNo, dayNo, userId));
    }

    @PutMapping("/{pathId}/weeks/{weekNo}/days/{dayNo}/questions/{questionId}/draft")
    public ApiResponse<SaveLearningPathAnswerDraftResponse> saveAnswerDraft(
            @Pattern(regexp = OBJECT_ID_PATTERN, message = "pathId không hợp lệ")
            @PathVariable String pathId,
            @Min(1) @Max(3) @PathVariable int weekNo,
            @Min(1) @Max(5) @PathVariable int dayNo,
            @NotBlank @Size(max = 100) @PathVariable String questionId,
            @Pattern(regexp = OBJECT_ID_PATTERN, message = "userId không hợp lệ")
            @RequestParam String userId,
            @Valid @RequestBody SaveLearningPathAnswerDraftRequest request
    ) {
        return ApiResponse.ok(answerDraftService.save(pathId, weekNo, dayNo,
                questionId, userId, request));
    }
}
