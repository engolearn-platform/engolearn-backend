package fit.iuh.engolearn.topic.controller;

import fit.iuh.engolearn.shared.web.ApiResponse;
import fit.iuh.engolearn.topic.dto.response.TopicMediaReferenceResponse;
import fit.iuh.engolearn.topic.model.enums.TopicMediaKind;
import fit.iuh.engolearn.topic.service.TopicMediaService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/management/topics/{topicId}/media")
@RequiredArgsConstructor
public class TopicMediaController {
    private final TopicMediaService media;

    @PostMapping("/{kind}")
    public ApiResponse<TopicMediaReferenceResponse> upload(@PathVariable String topicId, @PathVariable String kind,
                                                            @RequestParam("file") MultipartFile file) {
        TopicMediaKind mediaKind;
        try {
            mediaKind = TopicMediaKind.valueOf(kind.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new fit.iuh.engolearn.media.contract.exception.MediaUploadRejectedException("Media kind must be image or audio");
        }
        return ApiResponse.ok(new TopicMediaReferenceResponse(media.upload(topicId, mediaKind, file)));
    }

    @GetMapping("/{mediaId}")
    public ApiResponse<Void> resolve(@PathVariable String topicId, @PathVariable String mediaId,
                                     HttpServletResponse response) {
        var delivery = media.resolve(topicId, mediaId);
        response.setStatus(HttpStatus.FOUND.value());
        response.setHeader(HttpHeaders.LOCATION, delivery.url().toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return ApiResponse.ok(null);
    }
}
