package fit.iuh.engolearn.placement.dto.response;

import java.util.List;

public record PlacementAudioMigrationResponse(List<String> missingHashes, long linkedDocuments) {}
