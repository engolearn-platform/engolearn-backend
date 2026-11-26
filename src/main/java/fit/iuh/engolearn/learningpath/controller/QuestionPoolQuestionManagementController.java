package fit.iuh.engolearn.learningpath.controller;

import fit.iuh.engolearn.learningpath.dto.request.ListQuestionPoolQuestionsRequest;
import fit.iuh.engolearn.learningpath.dto.response.QuestionPoolQuestionDetailResponse;
import fit.iuh.engolearn.learningpath.dto.response.QuestionPoolQuestionListResponse;
import fit.iuh.engolearn.learningpath.service.QuestionPoolQuestionManagementService;
import fit.iuh.engolearn.shared.web.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/management/learning-paths/questions")
@RequiredArgsConstructor
public class QuestionPoolQuestionManagementController {
    private final QuestionPoolQuestionManagementService questions;

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
