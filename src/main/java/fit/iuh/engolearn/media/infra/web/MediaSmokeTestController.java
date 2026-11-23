package fit.iuh.engolearn.media.infra.web;

import fit.iuh.engolearn.media.contract.MediaManagement;
import fit.iuh.engolearn.media.contract.dto.MediaDeliveryDto;
import fit.iuh.engolearn.media.contract.dto.MediaUploadResultDto;
import fit.iuh.engolearn.media.contract.dto.UploadMediaCommandDto;
import fit.iuh.engolearn.media.contract.enums.MediaAccessPolicy;
import fit.iuh.engolearn.shared.web.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * Temporary HTTP adapter for manually verifying the complete Media upload and delivery flow.
 *
 * <p>Disabled by default. Enable only in a local development environment and keep the
 * matching SecurityConfig rule restricted to ADMIN.</p>
 */
@RestController
@RequestMapping("/api/v1/media/smoke")
@ConditionalOnProperty(
        prefix = "engolearn.media.smoke-test",
        name = "enabled",
        havingValue = "true")
public class MediaSmokeTestController {

    private static final String SMOKE_TEST_PURPOSE = "MEDIA_SMOKE_TEST";

    private final MediaManagement mediaManagement;

    public MediaSmokeTestController(MediaManagement mediaManagement) {
        this.mediaManagement = mediaManagement;
    }

    @PostMapping(value = "/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<MediaUploadResultDto> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "PUBLIC") MediaAccessPolicy accessPolicy,
            HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");

        MediaUploadResultDto result;
        try (InputStream content = file.getInputStream()) {
            result = mediaManagement.upload(new UploadMediaCommandDto(
                    SMOKE_TEST_PURPOSE,
                    accessPolicy,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getSize(),
                    content));
        }

        response.setStatus(HttpStatus.CREATED.value());
        return ApiResponse.ok(result);
    }

    @GetMapping("/{mediaId}/delivery")
    public ApiResponse<MediaDeliveryDto> resolve(
            @PathVariable String mediaId,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.ok(mediaManagement.resolve(mediaId));
    }
}
