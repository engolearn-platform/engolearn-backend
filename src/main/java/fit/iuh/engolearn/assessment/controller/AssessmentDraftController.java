package fit.iuh.engolearn.assessment.controller;

import fit.iuh.engolearn.assessment.dto.request.*;
import fit.iuh.engolearn.assessment.dto.response.*;
import fit.iuh.engolearn.assessment.service.AssessmentDraftService;
import fit.iuh.engolearn.shared.web.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/assessment/admin/drafts")
public class AssessmentDraftController {
    private final AssessmentDraftService drafts;
    public AssessmentDraftController(AssessmentDraftService drafts) { this.drafts = drafts; }

    @ModelAttribute
    void noCache(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
    }

    @GetMapping
    public ApiResponse<List<DraftSummaryResponse>> list(@RequestParam(defaultValue="0") @Min(0) int page) {
        return ApiResponse.ok(drafts.list(page));
    }

    @GetMapping("/skill-catalog")
    public ApiResponse<List<AssessmentSkillOptionResponse>> skillCatalog() {
        return ApiResponse.ok(drafts.skillCatalog());
    }

    @GetMapping("/{series}")
    public ApiResponse<AssessmentDraftResponse> get(@PathVariable String series) {
        return ApiResponse.ok(drafts.get(series));
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AssessmentDraftResponse> create(@Valid @RequestBody CreateDraftRequest input) {
        return ApiResponse.ok(drafts.create(input));
    }

    @PutMapping("/{series}")
    public ApiResponse<AssessmentDraftResponse> save(@PathVariable String series, @Valid @RequestBody DraftContentRequest input) {
        return ApiResponse.ok(drafts.save(series, input));
    }
}
