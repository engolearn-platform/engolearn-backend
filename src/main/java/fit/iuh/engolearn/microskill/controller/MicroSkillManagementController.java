package fit.iuh.engolearn.microskill.controller;

import fit.iuh.engolearn.microskill.service.MicroSkillService;
import fit.iuh.engolearn.microskill.dto.request.*;
import fit.iuh.engolearn.microskill.dto.response.*;
import fit.iuh.engolearn.shared.web.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/management/micro-skills")
@RequiredArgsConstructor
public class MicroSkillManagementController {
    private final MicroSkillService service;

    @GetMapping
    public ApiResponse<MicroSkillListResponse> list(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<MicroSkillResponse> detail(@PathVariable @NotBlank @Size(max = 100) String id, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.ok(service.detail(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MicroSkillResponse> create(@Valid @RequestBody CreateMicroSkillRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<MicroSkillResponse> update(@PathVariable @NotBlank @Size(max = 100) String id,
            @Valid @RequestBody UpdateMicroSkillRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<DeleteMicroSkillResponse> delete(@PathVariable @NotBlank @Size(max = 100) String id,
            @RequestParam @Min(1) @Max(2147483646) int revision, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.ok(service.delete(id, revision));
    }

}
