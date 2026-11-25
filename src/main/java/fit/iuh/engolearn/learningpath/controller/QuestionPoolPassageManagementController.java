package fit.iuh.engolearn.learningpath.controller;

import fit.iuh.engolearn.learningpath.dto.request.CreateQuestionPoolPassageRequest;
import fit.iuh.engolearn.learningpath.dto.response.QuestionPoolPassageResponse;
import fit.iuh.engolearn.learningpath.service.QuestionPoolPassageManagementService;
import fit.iuh.engolearn.shared.web.ApiResponse;
import fit.iuh.engolearn.learningpath.dto.request.ListQuestionPoolPassagesRequest;
import fit.iuh.engolearn.learningpath.dto.response.QuestionPoolPassageListResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import fit.iuh.engolearn.learningpath.dto.response.QuestionPoolPassageDetailResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PathVariable;
import jakarta.validation.Valid;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/management/learning-paths/passages")
@RequiredArgsConstructor
public class QuestionPoolPassageManagementController {
    private final QuestionPoolPassageManagementService passages;

    @GetMapping
    public ApiResponse<QuestionPoolPassageListResponse> list(
            @Valid @ModelAttribute ListQuestionPoolPassagesRequest request) {
        return ApiResponse.ok(passages.list(request));
    }

    @GetMapping("/{passageId}")
    public ApiResponse<QuestionPoolPassageDetailResponse> detail(
            @PathVariable @NotBlank @Size(max = 100) String passageId, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.ok(passages.detail(passageId));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<QuestionPoolPassageResponse> create(
            @Valid @RequestPart("passage") CreateQuestionPoolPassageRequest request,
            @RequestPart(value = "audio", required = false) MultipartFile audio) throws IOException {
        try (var input = audio == null ? null : audio.getInputStream()) {
            return ApiResponse.ok(passages.create(request, input,
                    audio == null ? null : audio.getOriginalFilename(),
                    audio == null ? null : audio.getContentType(), audio == null ? 0 : audio.getSize()));
        }
    }
}
