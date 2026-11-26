package fit.iuh.engolearn.learningpath.controller;

import fit.iuh.engolearn.learningpath.dto.request.ListQuestionPoolQuestionsRequest;
import fit.iuh.engolearn.learningpath.dto.request.CreateQuestionPoolQuestionRequest;
import fit.iuh.engolearn.learningpath.dto.request.UpdateQuestionPoolQuestionRequest;
import fit.iuh.engolearn.learningpath.dto.request.CopyQuestionPoolQuestionRequest;
import fit.iuh.engolearn.learningpath.dto.request.DeleteQuestionPoolQuestionRequest;
import fit.iuh.engolearn.learningpath.dto.request.PublishQuestionPoolQuestionRequest;
import fit.iuh.engolearn.learningpath.dto.response.PublishQuestionPoolQuestionResponse;
import fit.iuh.engolearn.learningpath.dto.response.QuestionPoolQuestionReadinessResponse;
import fit.iuh.engolearn.learningpath.service.QuestionPoolQuestionReadinessService;
import fit.iuh.engolearn.learningpath.dto.response.QuestionPoolQuestionMutationResponse;
import fit.iuh.engolearn.learningpath.dto.response.DeleteQuestionPoolQuestionResponse;
import fit.iuh.engolearn.learningpath.dto.response.QuestionPoolQuestionDetailResponse;
import fit.iuh.engolearn.learningpath.dto.response.QuestionPoolQuestionListResponse;
import fit.iuh.engolearn.learningpath.service.QuestionPoolQuestionManagementService;
import fit.iuh.engolearn.shared.web.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/management/learning-paths/questions")
@RequiredArgsConstructor
public class QuestionPoolQuestionManagementController {
    private final QuestionPoolQuestionManagementService questions;
    private final QuestionPoolQuestionReadinessService readiness;

    @GetMapping("/{questionId}/readiness")
    public ApiResponse<QuestionPoolQuestionReadinessResponse> readiness(
            @PathVariable @NotBlank @Size(max = 100) String questionId, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.ok(readiness.check(questionId));
    }

    @PostMapping("/{questionId}/publish")
    public ApiResponse<PublishQuestionPoolQuestionResponse> publish(
            @PathVariable @NotBlank @Size(max = 100) String questionId,
            @Valid @RequestBody PublishQuestionPoolQuestionRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.ok(questions.publish(questionId, request));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<QuestionPoolQuestionMutationResponse> create(@Valid @RequestBody CreateQuestionPoolQuestionRequest request,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.ok(questions.create(request));
    }

    @PatchMapping("/{questionId}")
    public ApiResponse<QuestionPoolQuestionMutationResponse> update(
            @PathVariable @NotBlank @Size(max = 100) String questionId,
            @Valid @RequestBody UpdateQuestionPoolQuestionRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.ok(questions.update(questionId, request));
    }

    @DeleteMapping("/{questionId}")
    public ApiResponse<DeleteQuestionPoolQuestionResponse> delete(
            @PathVariable @NotBlank @Size(max = 100) String questionId,
            @Valid @ModelAttribute DeleteQuestionPoolQuestionRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.ok(questions.delete(questionId, request.revision()));
    }

    @PostMapping("/{questionId}/copy")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<QuestionPoolQuestionMutationResponse> copy(
            @PathVariable @NotBlank @Size(max = 100) String questionId,
            @Valid @RequestBody CopyQuestionPoolQuestionRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.ok(questions.copy(questionId, request.revision()));
    }

    @GetMapping
    public ApiResponse<QuestionPoolQuestionListResponse> list(
            @Valid @ModelAttribute ListQuestionPoolQuestionsRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.ok(questions.list(request));
    }

    @GetMapping("/{questionId}")
    public ApiResponse<QuestionPoolQuestionDetailResponse> detail(
            @PathVariable @NotBlank @Size(max = 100) String questionId, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.ok(questions.detail(questionId));
    }
}
