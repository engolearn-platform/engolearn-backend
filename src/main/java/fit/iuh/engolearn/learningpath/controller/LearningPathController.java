
package fit.iuh.engolearn.learningpath.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import fit.iuh.engolearn.shared.web.ApiResponse;
import fit.iuh.engolearn.shared.security.impl.AuthenticatedUserPrincipal;
import fit.iuh.engolearn.learningpath.dto.response.LearningPathDetailResponse;
import fit.iuh.engolearn.learningpath.dto.response.LearningPathListResponse;
import fit.iuh.engolearn.learningpath.dto.request.LearningPathListStatus;
import fit.iuh.engolearn.learningpath.dto.request.CreateLearningPathRequest;
import fit.iuh.engolearn.learningpath.dto.request.CreateLearningPathByAssessmentRequest;
import fit.iuh.engolearn.learningpath.dto.request.SaveLearningPathAnswerDraftRequest;
import fit.iuh.engolearn.learningpath.dto.response.CreateLearningPathResponse;
import fit.iuh.engolearn.learningpath.dto.response.LearningPathRecommendationResponse;
import fit.iuh.engolearn.learningpath.dto.response.SaveLearningPathAnswerDraftResponse;
import fit.iuh.engolearn.learningpath.dto.response.StartLearningPathDayResponse;
import fit.iuh.engolearn.learningpath.dto.response.SubmitLearningPathDayResponse;
import fit.iuh.engolearn.learningpath.dto.response.LearningPathDayResultResponse;
import fit.iuh.engolearn.learningpath.service.LearningPathDayResultService;
import fit.iuh.engolearn.learningpath.service.LearningPathDaySubmissionService;
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
    private final LearningPathDaySubmissionService submissionService;
    private final LearningPathDayResultService dayResultService;

    public LearningPathController(LearningPathService learningPathService,
                                  LearningPathRecommendationService recommendationService,
                                  LearningPathCreationService creationService,
                                  LearningPathDayPracticeService dayPracticeService,
                                  LearningPathAnswerDraftService answerDraftService,
                                  LearningPathDaySubmissionService submissionService,
                                  LearningPathDayResultService dayResultService) {
        this.learningPathService = learningPathService;
        this.recommendationService = recommendationService;
        this.creationService = creationService;
        this.dayPracticeService = dayPracticeService;
        this.answerDraftService = answerDraftService;
        this.submissionService = submissionService;
        this.dayResultService = dayResultService;
    }

    @GetMapping("/recommendation")
    public ApiResponse<LearningPathRecommendationResponse> getRecommendation(
            @Pattern(regexp = OBJECT_ID_PATTERN, message = "attemptId không hợp lệ")
            @RequestParam String attemptId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return ApiResponse.ok(recommendationService.recommend(principal.getId(), attemptId));
    }

    @GetMapping("/recommendation/by-assessment")
    public ApiResponse<LearningPathRecommendationResponse> getRecommendationByAssessment(
            @NotBlank(message = "assessmentAttemptId không được để trống")
            @Size(max = 100, message = "assessmentAttemptId không được vượt quá 100 ký tự")
            @RequestParam String assessmentAttemptId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return ApiResponse.ok(recommendationService.recommendForAssessment(principal.getId(), assessmentAttemptId));
    }

    @PostMapping
    public ApiResponse<CreateLearningPathResponse> createLearningPath(
            @Valid @RequestBody CreateLearningPathRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return ApiResponse.ok(creationService.create(request, principal.getId()));
    }

    @PostMapping("/by-assessment")
    public ApiResponse<CreateLearningPathResponse> createLearningPathByAssessment(
            @Valid @RequestBody CreateLearningPathByAssessmentRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return ApiResponse.ok(creationService.createForAssessment(request, principal.getId()));
    }

    @GetMapping
    public ApiResponse<LearningPathListResponse> getLearningPaths(
            @RequestParam(defaultValue = "ACTIVE") LearningPathListStatus status,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return ApiResponse.ok(learningPathService.getByUserId(principal.getId(), status));
    }

    @GetMapping("/search")
    public ApiResponse<LearningPathListResponse> searchLearningPaths(
            @NotBlank(message = "keyword không được để trống")
            @Size(max = 100, message = "keyword không được vượt quá 100 ký tự")
            @RequestParam String keyword,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return ApiResponse.ok(learningPathService.search(principal.getId(), keyword));
    }

    @GetMapping("/{pathId}")
    public ApiResponse<LearningPathDetailResponse> getLearningPathDetail(
            @Pattern(regexp = OBJECT_ID_PATTERN, message = "pathId không hợp lệ")
            @PathVariable String pathId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return ApiResponse.ok(learningPathService.getDetail(pathId, principal.getId()));
    }

    @PostMapping("/{pathId}/weeks/{weekNo}/days/{dayNo}/start")
    public ApiResponse<StartLearningPathDayResponse> startDay(
            @Pattern(regexp = OBJECT_ID_PATTERN, message = "pathId không hợp lệ")
            @PathVariable String pathId,
            @Min(1) @Max(3) @PathVariable int weekNo,
            @Min(1) @Max(5) @PathVariable int dayNo,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return ApiResponse.ok(dayPracticeService.start(pathId, weekNo, dayNo, principal.getId()));
    }

    @PutMapping("/{pathId}/weeks/{weekNo}/days/{dayNo}/questions/{questionId}/draft")
    public ApiResponse<SaveLearningPathAnswerDraftResponse> saveAnswerDraft(
            @Pattern(regexp = OBJECT_ID_PATTERN, message = "pathId không hợp lệ")
            @PathVariable String pathId,
            @Min(1) @Max(3) @PathVariable int weekNo,
            @Min(1) @Max(5) @PathVariable int dayNo,
            @NotBlank @Size(max = 100) @PathVariable String questionId,
            @Valid @RequestBody SaveLearningPathAnswerDraftRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return ApiResponse.ok(answerDraftService.save(pathId, weekNo, dayNo,
                questionId, principal.getId(), request));
    }

    @PostMapping("/{pathId}/weeks/{weekNo}/days/{dayNo}/submit")
    public ApiResponse<SubmitLearningPathDayResponse> submitDay(
            @Pattern(regexp = OBJECT_ID_PATTERN, message = "pathId không hợp lệ") @PathVariable String pathId,
            @Min(1) @Max(3) @PathVariable int weekNo,
            @Min(1) @Max(5) @PathVariable int dayNo,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return ApiResponse.ok(submissionService.submit(pathId, weekNo, dayNo, principal.getId()));
    }

    @GetMapping("/{pathId}/weeks/{weekNo}/days/{dayNo}/result")
    public ApiResponse<LearningPathDayResultResponse> getDayResult(
            @Pattern(regexp = OBJECT_ID_PATTERN, message = "pathId không hợp lệ") @PathVariable String pathId,
            @Min(1) @Max(3) @PathVariable int weekNo,
            @Min(1) @Max(5) @PathVariable int dayNo,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return ApiResponse.ok(dayResultService.getResult(pathId, weekNo, dayNo, principal.getId()));
    }
}
