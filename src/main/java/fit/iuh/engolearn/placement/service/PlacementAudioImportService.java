package fit.iuh.engolearn.placement.service;

import fit.iuh.engolearn.placement.dto.response.PlacementAdminQuestionMediaResponse.ItemResponse;
import fit.iuh.engolearn.placement.dto.response.PlacementAudioMigrationResponse;
import java.io.InputStream;

public interface PlacementAudioImportService {
    ItemResponse upload(String filename, long size, InputStream content);
    PlacementAudioMigrationResponse preflight();
    PlacementAudioMigrationResponse linkImported();
}
