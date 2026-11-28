package fit.iuh.engolearn.companion.service;

import java.time.Duration;
import java.util.List;

public interface MemoryEmbeddingService {
    List<Float> embedQuery(String query, Duration timeout);
    List<Float> embedDocument(String content, Duration timeout);
}
