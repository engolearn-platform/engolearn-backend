package fit.iuh.engolearn.placement.controller;

import fit.iuh.engolearn.placement.dto.response.PlacementAdminQuestionMediaResponse.ItemResponse;
import fit.iuh.engolearn.placement.dto.response.PlacementAudioMigrationResponse;
import fit.iuh.engolearn.placement.service.PlacementAudioImportService;
import fit.iuh.engolearn.shared.web.ApiResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/placement/admin/questions/media")
public class PlacementAudioController {
    private final PlacementAudioImportService imports;
    @PostMapping(consumes = "multipart/form-data")
    public ApiResponse<ItemResponse> upload(@RequestParam("file") MultipartFile file) throws IOException {
        try (var content = file.getInputStream()) {
            return ApiResponse.ok(imports.upload(file.getOriginalFilename(), file.getSize(), content));
        }
    }
    @GetMapping("/preflight")
    public ApiResponse<PlacementAudioMigrationResponse> preflight() { return ApiResponse.ok(imports.preflight()); }
    @PostMapping("/migrate")
    public ApiResponse<PlacementAudioMigrationResponse> migrate() { return ApiResponse.ok(imports.linkImported()); }
}
