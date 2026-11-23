package fit.iuh.engolearn.assessment.controller;

import fit.iuh.engolearn.assessment.exception.AssessmentException;
import fit.iuh.engolearn.assessment.service.AssessmentCloudAudio;
import fit.iuh.engolearn.shared.web.ApiResponse;
import jakarta.validation.constraints.Pattern;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/assessment/admin/media")
public class AssessmentAudioAdminController {
    private final AssessmentCloudAudio audio;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, String>> upload(@RequestParam MultipartFile file,
            @RequestParam(required = false) @Pattern(regexp = "[0-9a-fA-F]{64}") String sha256,
            @RequestParam(defaultValue = "false") boolean soundCheck) {
        try (var stream = file.getInputStream()) {
            return ApiResponse.ok(Map.of("sha256", audio.upload(stream, sha256, soundCheck)));
        } catch (IOException ex) {
            throw new AssessmentException(422, "INVALID_ASSESSMENT_AUDIO", "Không đọc được tệp WAV.");
        }
    }
}
