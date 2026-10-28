package fit.iuh.engolearn.topic.controller;

import fit.iuh.engolearn.shared.web.ApiResponse;
import fit.iuh.engolearn.topic.dto.request.CreateLocalLearnerRequest;
import fit.iuh.engolearn.topic.dto.response.LocalLearnerResponse;
import fit.iuh.engolearn.topic.service.LocalLearnerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import java.net.InetAddress;
import java.net.UnknownHostException;

@RestController
@RequestMapping("/api/v1/topic-learning/local-profiles")
public class LocalLearnerController {
    private final LocalLearnerService profiles;

    public LocalLearnerController(LocalLearnerService profiles) { this.profiles = profiles; }

    @PostMapping
    public ApiResponse<LocalLearnerResponse> create(@Valid @RequestBody CreateLocalLearnerRequest body, HttpServletRequest request) {
        try {
            if (!InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Local profile setup is loopback only");
            }
        } catch (UnknownHostException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Local profile setup is loopback only");
        }
        return ApiResponse.ok(profiles.create(body));
    }
}
