package fit.iuh.engolearn.assessment.controller;

import fit.iuh.engolearn.assessment.dto.request.AssessmentImportAudio;
import fit.iuh.engolearn.assessment.dto.response.AssessmentDraftResponse;
import fit.iuh.engolearn.assessment.dto.response.AssessmentImportPreview;
import fit.iuh.engolearn.assessment.exception.AssessmentException;
import fit.iuh.engolearn.assessment.service.impl.AssessmentDraftImportService;
import fit.iuh.engolearn.shared.web.ApiResponse;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import static fit.iuh.engolearn.assessment.utils.AssessmentWorkbook.invalid;

@RestController
@RequestMapping("/api/v1/assessment/admin/draft-imports")
@RequiredArgsConstructor
public class AssessmentDraftImportController {
    private final AssessmentDraftImportService imports;

    @GetMapping("/template")
    public ApiResponse<Map<String, String>> template() {
        try {
            return ApiResponse.ok(Map.of("base64", Base64.getEncoder().encodeToString(imports.template())));
        } catch (IOException error) {
            throw new AssessmentException(503, "IMPORT_TEMPLATE_UNAVAILABLE", "Mẫu Excel hiện không tải được.", error);
        }
    }

    @PostMapping(value = "/inspect-audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AssessmentImportAudio> inspectAudio(@RequestPart MultipartFile file) {
        try (var input = file.getInputStream()) {
            return ApiResponse.ok(imports.inspect(file.getOriginalFilename(), input));
        } catch (IOException error) {
            throw invalid("Không đọc được WAV " + file.getOriginalFilename() + ".");
        }
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AssessmentImportPreview> preview(@RequestPart MultipartFile workbook,
                                                       @RequestPart List<AssessmentImportAudio> manifest) {
        try (var input = workbook.getInputStream()) {
            requireWorkbook(workbook);
            return ApiResponse.ok(imports.preview(input, manifest));
        } catch (IOException error) {
            throw invalid("Không đọc được workbook.");
        }
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AssessmentDraftResponse> create(@RequestPart MultipartFile workbook,
                                                       @RequestPart List<AssessmentImportAudio> manifest,
                                                       @RequestParam String token) {
        try (var input = workbook.getInputStream()) {
            requireWorkbook(workbook);
            return ApiResponse.ok(imports.create(input, manifest, token));
        } catch (IOException error) {
            throw invalid("Không đọc được workbook.");
        }
    }

    private static void requireWorkbook(MultipartFile workbook) {
        String name = workbook.getOriginalFilename();
        if (name == null || !name.toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx")) {
            throw invalid("Chọn tệp Excel Workbook .xlsx.");
        }
    }
}
