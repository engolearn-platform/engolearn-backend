package fit.iuh.engolearn.placement.controller;

import fit.iuh.engolearn.placement.dto.request.CreatePlacementTestSetRequest;
import fit.iuh.engolearn.placement.dto.request.PlacementTestSetRevisionRequest;
import fit.iuh.engolearn.placement.dto.request.UpdatePlacementTestSetRequest;
import fit.iuh.engolearn.placement.dto.response.PlacementAdminTestSetPageResponse;
import fit.iuh.engolearn.placement.dto.response.PlacementAdminTestSetResponse;
import fit.iuh.engolearn.placement.model.enums.PlacementTestSetStatus;
import fit.iuh.engolearn.placement.service.PlacementAdminTestSetService;
import fit.iuh.engolearn.shared.web.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/placement/admin/test-sets")
@ConditionalOnProperty(prefix = "engolearn.placement.persistence", name = "mode", havingValue = "mongo")
public class PlacementAdminTestSetController {
    private final PlacementAdminTestSetService service;

    @GetMapping
    public ApiResponse<PlacementAdminTestSetPageResponse> list(
            @RequestParam(required = false) @Size(max = 160) String search,
            @RequestParam(required = false) PlacementTestSetStatus status,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(service.list(search, status, page, size));
    }

    @GetMapping({"/{id}", "/{id}/preview"})
    public ApiResponse<PlacementAdminTestSetResponse> get(@PathVariable @NotBlank @Size(max = 200) String id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PlacementAdminTestSetResponse> create(@Valid @RequestBody CreatePlacementTestSetRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<PlacementAdminTestSetResponse> update(@PathVariable @NotBlank @Size(max = 200) String id,
            @Valid @RequestBody UpdatePlacementTestSetRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<PlacementAdminTestSetResponse> publish(@PathVariable @NotBlank @Size(max = 200) String id,
            @Valid @RequestBody UpdatePlacementTestSetRequest request) {
        return ApiResponse.ok(service.publish(id, request));
    }

    @PostMapping("/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PlacementAdminTestSetResponse> newVersion(@PathVariable @NotBlank @Size(max = 200) String id) {
        return ApiResponse.ok(service.newVersion(id));
    }

    @PostMapping("/{id}/archive")
    public ApiResponse<PlacementAdminTestSetResponse> archive(@PathVariable @NotBlank @Size(max = 200) String id,
            @Valid @RequestBody PlacementTestSetRevisionRequest request) {
        return ApiResponse.ok(service.archive(id, request.revision()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> delete(@PathVariable @NotBlank @Size(max = 200) String id,
            @RequestParam @PositiveOrZero long revision) {
        service.delete(id, revision);
        return null;
    }

    @ModelAttribute
    private void disableCaching(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }
}
