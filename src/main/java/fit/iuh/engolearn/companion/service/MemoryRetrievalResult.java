package fit.iuh.engolearn.companion.service;

import java.util.List;

public record MemoryRetrievalResult(String promptContext, List<MemoryReference> references) {
    public MemoryRetrievalResult {
        references = List.copyOf(references);
    }

    public record MemoryReference(String id, long version) { }
}
