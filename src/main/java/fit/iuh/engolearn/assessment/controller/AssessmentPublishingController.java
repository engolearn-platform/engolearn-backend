package fit.iuh.engolearn.assessment.controller;

import fit.iuh.engolearn.assessment.dto.request.AssessmentDraftRevisionRequest;
import fit.iuh.engolearn.assessment.dto.response.AssessmentDraftResponse;
import fit.iuh.engolearn.assessment.dto.response.AssessmentMediaResponse;
import fit.iuh.engolearn.assessment.dto.response.ManagedAssessmentPaperResponse;
import fit.iuh.engolearn.assessment.service.AssessmentPublishing;
import fit.iuh.engolearn.shared.web.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/assessment/admin")
public class AssessmentPublishingController {
    private final AssessmentPublishing publishing;

    @ModelAttribute
    void noCache(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
    }

    @PostMapping("/drafts/{series}/preview")
    public ApiResponse<AssessmentDraftResponse> preview(@PathVariable String series,
            @Valid @RequestBody AssessmentDraftRevisionRequest request) {
        return ApiResponse.ok(publishing.preview(series, request.revision()));
    }

    @GetMapping("/drafts/{series}/preview/audio/{unit}")
    public ApiResponse<AssessmentMediaResponse> previewAudio(@PathVariable String series,
            @PathVariable @Min(0) int unit, @RequestParam String revision) {
        return ApiResponse.ok(publishing.previewAudio(series, revision, unit));
    }

    @PostMapping("/drafts/{series}/review")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> review(@PathVariable String series, @Valid @RequestBody AssessmentDraftRevisionRequest request) {
        publishing.review(series, request.revision());
        return ApiResponse.ok(null);
    }

    @PostMapping("/drafts/{series}/publish")
    public ApiResponse<ManagedAssessmentPaperResponse> publish(@PathVariable String series,
            @Valid @RequestBody AssessmentDraftRevisionRequest request) {
        return ApiResponse.ok(publishing.publish(series, request.revision()));
    }

    @DeleteMapping("/drafts/{series}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> delete(@PathVariable String series, @RequestParam String revision) {
        publishing.deleteDraft(series, revision);
        return ApiResponse.ok(null);
    }

    @GetMapping("/papers")
    public ApiResponse<List<ManagedAssessmentPaperResponse>> papers(@RequestParam(defaultValue = "0") @Min(0) int page) {
        return ApiResponse.ok(publishing.papers(page));
    }

    @PostMapping("/papers/{series}/{version}/archive")
    public ApiResponse<ManagedAssessmentPaperResponse> archive(@PathVariable String series,
            @PathVariable @Min(1) int version) {
        return ApiResponse.ok(publishing.archive(series, version));
    }
}
