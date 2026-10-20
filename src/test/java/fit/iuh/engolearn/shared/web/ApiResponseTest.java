package fit.iuh.engolearn.shared.web;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests of the {@link ApiResponse} success envelope.
 *
 * <p>What these tests prove: the factory keeps the payload and stamps the envelope, and the JSON
 * sent to the frontend carries both envelope fields with an ISO-8601 timestamp. What they do not
 * prove: the HTTP wiring, which the controller tests cover.
 */
class ApiResponseTest {

    @Test
    void okCarriesTheDataAndANonNullTimestamp() {
        Instant before = Instant.now();

        ApiResponse<String> response = ApiResponse.ok("payload");

        assertThat(response.data()).isEqualTo("payload");
        assertThat(response.timestamp()).isNotNull();
        assertThat(response.timestamp()).isAfterOrEqualTo(before);
    }

    @Test
    void serializationWritesDataAndTimestamp() {
        JsonMapper mapper = JsonMapper.builder().build();

        JsonNode json = mapper.readTree(mapper.writeValueAsString(ApiResponse.ok("payload")));

        assertThat(json.has("data")).isTrue();
        assertThat(json.get("data").asString()).isEqualTo("payload");
        assertThat(json.has("timestamp")).isTrue();
        assertThat(Instant.parse(json.get("timestamp").asString())).isNotNull();
    }
}
