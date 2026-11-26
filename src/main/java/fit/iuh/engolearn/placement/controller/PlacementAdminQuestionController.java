package fit.iuh.engolearn.placement.controller;

import fit.iuh.engolearn.placement.dto.request.PlacementAdminQuestionImportRequest;
import fit.iuh.engolearn.placement.dto.request.PlacementAdminQuestionRequest;
import fit.iuh.engolearn.placement.dto.request.PlacementAdminQuestionStatusRequest;
import fit.iuh.engolearn.placement.dto.response.PlacementAdminQuestionMediaResponse;
import fit.iuh.engolearn.placement.dto.response.PlacementAdminQuestionResponse;
import fit.iuh.engolearn.placement.dto.response.PlacementAdminQuestionResponse.ImportResponse;
import fit.iuh.engolearn.placement.dto.response.PlacementAdminQuestionResponse.PageResponse;
import fit.iuh.engolearn.placement.service.PlacementAdminQuestionService;
import fit.iuh.engolearn.shared.web.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/placement/admin/questions")
@ConditionalOnProperty(prefix = "engolearn.placement.persistence", name = "mode", havingValue = "mongo")
public class PlacementAdminQuestionController {
    private final PlacementAdminQuestionService service;

    @GetMapping
    public ApiResponse<PageResponse> search(@RequestParam(defaultValue = "") @Size(max = 120) String search,
                                             @RequestParam(defaultValue = "") String skill,
                                             @RequestParam(defaultValue = "") String level,
                                             @RequestParam(defaultValue = "") String status,
                                             @RequestParam(defaultValue = "0") @Min(0) int page,
                                             @RequestParam(defaultValue = "100") @Min(1) @Max(100) int size,
                                             HttpServletResponse response) {
        noStore(response);
        return ApiResponse.ok(service.search(search, skill, level, status, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<PlacementAdminQuestionResponse> get(@PathVariable String id, HttpServletResponse response) {
        noStore(response);
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PlacementAdminQuestionResponse> create(@Valid @RequestBody PlacementAdminQuestionRequest request,
                                                               HttpServletResponse response) {
        noStore(response);
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<PlacementAdminQuestionResponse> update(@PathVariable String id,
                                                               @Valid @RequestBody PlacementAdminQuestionRequest request,
                                                               HttpServletResponse response) {
        noStore(response);
        return ApiResponse.ok(service.update(id, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<PlacementAdminQuestionResponse> changeStatus(@PathVariable String id,
            @Valid @RequestBody PlacementAdminQuestionStatusRequest request, HttpServletResponse response) {
        noStore(response);
        return ApiResponse.ok(service.changeStatus(id, request));
    }

    @PostMapping("/import")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ImportResponse> importQuestions(@Valid @RequestBody PlacementAdminQuestionImportRequest request,
                                                         HttpServletResponse response) {
        noStore(response);
        return ApiResponse.ok(service.importQuestions(request));
    }

    @GetMapping("/media")
    public ApiResponse<PlacementAdminQuestionMediaResponse> mediaCatalog(HttpServletResponse response) {
        noStore(response);
        return ApiResponse.ok(service.mediaCatalog());
    }

    private void noStore(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }
}
