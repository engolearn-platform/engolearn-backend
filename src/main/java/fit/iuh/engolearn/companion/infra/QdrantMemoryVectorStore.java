package fit.iuh.engolearn.companion.infra;

import fit.iuh.engolearn.companion.service.MemoryVectorStore;
import fit.iuh.engolearn.config.CompanionMemoryProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class QdrantMemoryVectorStore implements MemoryVectorStore {
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() { };
    private final CompanionMemoryProperties properties;
    private final AtomicBoolean collectionReady = new AtomicBoolean();

    public QdrantMemoryVectorStore(CompanionMemoryProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<VectorMatch> search(List<Float> vector, String learnerId, int limit, Duration timeout) {
        ensureCollection(timeout);
        Map<String, Object> response = invoke(timeout, () -> client(timeout).post()
                .uri(collectionPath() + "/points/query")
                .body(Map.of("query", vector, "limit", limit, "with_payload", List.of("learner_id", "version"),
                        "filter", Map.of("must", List.of(
                                Map.of("key", "learner_id", "match", Map.of("value", learnerId)),
                                Map.of("key", "retrieval_mode", "match", Map.of("value", "RELEVANT")))),
                        "score_threshold", properties.getSimilarityThreshold()))
                .retrieve().body(MAP_TYPE));
        Object resultObject = response.get("result");
        List<?> result = resultObject instanceof Map<?, ?> map ? list(map.get("points")) : List.of();
        List<VectorMatch> matches = new ArrayList<>();
        for (Object row : result) {
            if (row instanceof Map<?, ?> item && item.get("id") != null && item.get("payload") instanceof Map<?, ?> payload) {
                Object owner = payload.get("learner_id");
                Object version = payload.get("version");
                if (owner != null && version instanceof Number number) {
                    matches.add(new VectorMatch(item.get("id").toString(), owner.toString(), number.longValue()));
                }
            }
        }
        return List.copyOf(matches);
    }

    @Override
    public void upsert(String pointId, List<Float> vector, Map<String, Object> payload, Duration timeout) {
        ensureCollection(timeout);
        Map<String, Object> point = Map.of("id", pointId, "vector", vector, "payload", payload);
        invoke(timeout, () -> client(timeout).put().uri(collectionPath() + "/points?wait=true")
                .body(Map.of("points", List.of(point))).retrieve().body(MAP_TYPE));
    }

    @Override
    public void delete(String pointId, Duration timeout) {
        try {
            invoke(timeout, () -> client(timeout).post().uri(collectionPath() + "/points/delete?wait=true")
                    .body(Map.of("points", List.of(pointId))).retrieve().body(MAP_TYPE));
        } catch (RestClientResponseException missingCollection) {
            if (missingCollection.getStatusCode().value() != 404) throw missingCollection;
        }
    }

    @Override
    public VectorPage scroll(String offset, int limit, Duration timeout) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("limit", limit);
        body.put("with_payload", true);
        body.put("with_vector", false);
        if (offset != null) body.put("offset", offset);
        Map<String, Object> response = invoke(timeout, () -> client(timeout).post().uri(collectionPath() + "/points/scroll")
                .body(body).retrieve().body(MAP_TYPE));
        Object resultValue = response.get("result");
        if (!(resultValue instanceof Map<?, ?> result)) return new VectorPage(List.of(), null);
        List<VectorPoint> points = new ArrayList<>();
        for (Object row : list(result.get("points"))) {
            if (row instanceof Map<?, ?> item && item.get("id") != null) {
                Object payloadValue = item.get("payload");
                Object owner = payloadValue instanceof Map<?, ?> payload ? payload.get("learner_id") : null;
                Object version = payloadValue instanceof Map<?, ?> payload ? payload.get("version") : null;
                points.add(new VectorPoint(item.get("id").toString(), owner == null ? null : owner.toString(),
                        version instanceof Number number ? number.longValue() : -1));
            }
        }
        Object next = result.get("next_page_offset");
        return new VectorPage(points, next == null ? null : next.toString());
    }

    private RestClient client(Duration timeout) {
        int millis = (int) Math.max(1, Math.min(Integer.MAX_VALUE, timeout.toMillis()));
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(millis);
        requestFactory.setReadTimeout(millis);
        return RestClient.builder().baseUrl(properties.getQdrantUrl()).requestFactory(requestFactory).build();
    }

    private String collectionPath() {
        return "/collections/" + properties.getCollection();
    }

    private void ensureCollection(Duration timeout) {
        if (collectionReady.get()) return;
        synchronized (collectionReady) {
            if (collectionReady.get()) return;
            try {
                invoke(timeout, () -> client(timeout).get().uri(collectionPath()).retrieve().body(MAP_TYPE));
            } catch (RestClientResponseException missing) {
                if (missing.getStatusCode().value() != 404) throw missing;
                invoke(timeout, () -> client(timeout).put().uri(collectionPath())
                        .body(Map.of("vectors", Map.of("size", properties.getDimensions(), "distance", "Cosine")))
                        .retrieve().body(MAP_TYPE));
            }
            collectionReady.set(true);
        }
    }

    private <T> T invoke(Duration timeout, java.util.function.Supplier<T> operation) {
        try {
            return operation.get();
        } catch (ResourceAccessException transientFailure) {
            return operation.get();
        } catch (RestClientResponseException responseFailure) {
            if (responseFailure.getStatusCode().is5xxServerError() || responseFailure.getStatusCode().value() == 429) {
                return operation.get();
            }
            throw responseFailure;
        }
    }

    private List<?> list(Object value) {
        return value instanceof List<?> items ? items : List.of();
    }
}
