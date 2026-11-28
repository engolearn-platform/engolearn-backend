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
@RequestMapping("/api/micro-skills")
@RequiredArgsConstructor
public class MicroSkillController {
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

}
