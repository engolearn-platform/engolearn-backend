package fit.iuh.engolearn.assessment.controller;

import fit.iuh.engolearn.assessment.contract.MicroSkillCatalog.Entry;
import fit.iuh.engolearn.assessment.dto.request.MicroSkillRequest;
import fit.iuh.engolearn.assessment.service.impl.MicroSkillService;
import fit.iuh.engolearn.shared.web.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/assessment/admin/micro-skills")
@RequiredArgsConstructor
public class MicroSkillAdminController {
    private final MicroSkillService catalog;

    @GetMapping
    public ApiResponse<List<Entry>> list() { return ApiResponse.ok(catalog.list()); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Entry> create(@Valid @RequestBody MicroSkillRequest.Create input) {
        return ApiResponse.ok(catalog.create(input));
    }

    @PatchMapping("/{code}")
    public ApiResponse<Entry> rename(@PathVariable String code, @Valid @RequestBody MicroSkillRequest.Rename input) {
        return ApiResponse.ok(catalog.rename(code, input.label()));
    }

    @PostMapping("/{code}/retire")
    public ApiResponse<Entry> retire(@PathVariable String code) { return ApiResponse.ok(catalog.retire(code)); }
}
