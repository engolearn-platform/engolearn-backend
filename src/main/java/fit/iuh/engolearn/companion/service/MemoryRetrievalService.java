package fit.iuh.engolearn.companion.service;

import java.time.Instant;

public interface MemoryRetrievalService {
    MemoryRetrievalResult retrieve(String learnerId, String query, Instant deadline);
}
