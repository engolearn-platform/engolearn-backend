package fit.iuh.engolearn.companion.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public interface MemoryVectorStore {
    List<VectorMatch> search(List<Float> vector, String learnerId, int limit, Duration timeout);
    void upsert(String pointId, List<Float> vector, Map<String, Object> payload, Duration timeout);
    void delete(String pointId, Duration timeout);
    VectorPage scroll(String offset, int limit, Duration timeout);

    record VectorMatch(String pointId, String learnerId, long version) { }
    record VectorPoint(String pointId, String learnerId, long version) { }
    record VectorPage(List<VectorPoint> points, String nextOffset) {
        public VectorPage { points = List.copyOf(points); }
    }
}
