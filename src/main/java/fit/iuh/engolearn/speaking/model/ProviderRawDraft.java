package fit.iuh.engolearn.speaking.model;

import java.util.Objects;

public record ProviderRawDraft(String candidateJson, ProviderMetadata metadata) {
    public ProviderRawDraft {
        if (candidateJson == null || candidateJson.isBlank()) {
            throw new IllegalArgumentException("candidateJson must be nonblank");
        }
        Objects.requireNonNull(metadata, "metadata");
    }

    public record ProviderMetadata(
            String model,
            Integer httpStatus,
            String responseId,
            String finishReason,
            Long promptTokenCount,
            Long candidateTokenCount,
            Long totalTokenCount
    ) {
        public boolean hasRequiredFields() {
            return model != null && !model.isBlank() && httpStatus != null;
        }
    }
}
