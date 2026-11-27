package fit.iuh.engolearn.companion.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.ObjectMapper;

/** Strict model input and bounded, provider-neutral response envelope. */
final class BusinessToolResponse {
    private final ObjectMapper mapper;
    BusinessToolResponse(ObjectMapper mapper) { this.mapper = mapper; }

    static final class InvalidArguments extends RuntimeException {}

    static Map<String, Object> args(Map<String, Object> input, String... keys) {
        if (input == null) throw new InvalidArguments();
        var allowed = Set.of(keys);
        if (input.entrySet().stream().anyMatch(e -> e.getKey() == null
                || !allowed.contains(e.getKey()) || e.getValue() == null)) throw new InvalidArguments();
        return input;
    }

    static String text(Map<String, Object> args, String key, int max, String fallback) {
        if (!args.containsKey(key)) return fallback;
        if (!(args.get(key) instanceof String s) || s.isBlank() || s.length() > max)
            throw new InvalidArguments();
        return s;
    }

    static String choice(Map<String, Object> args, String key, String fallback, String... values) {
        String value = text(args, key, 32, fallback);
        if (value == null || !Set.of(values).contains(value)) throw new InvalidArguments();
        return value;
    }

    static int limit(Map<String, Object> args, int min, int fallback) {
        if (!args.containsKey("limit")) return fallback;
        Object v = args.get("limit");
        if (!(v instanceof Number n)) throw new InvalidArguments();
        try {
            int value = new java.math.BigDecimal(n.toString()).intValueExact();
            if (value < min || value > 5) throw new InvalidArguments();
            return value;
        } catch (NumberFormatException | ArithmeticException invalid) {
            throw new InvalidArguments();
        }
    }

    static void countsOnly(Map<String, Object> args) {
        if (args.containsKey("limit") || args.containsKey("cursor")) throw new InvalidArguments();
    }

    String page(List<?> items, boolean more, String cursor, String coverage, Instant asOf,
            int limit, Map<String, Object> extra) {
        if (items == null || items.size() > limit || asOf == null
                || coverage == null || !Set.of("COMPLETE", "LIMITED").contains(coverage)
                || (cursor != null && (!more || cursor.isBlank() || cursor.length() > 512))
                || (more && cursor == null && "COMPLETE".equals(coverage))
                || (items.isEmpty() && cursor != null)) return error("INCONSISTENT_STATE");
        var data = new LinkedHashMap<String, Object>();
        data.put("items", items);
        data.put("hasMore", more);
        data.put("nextCursor", cursor);
        data.put("coverage", coverage);
        data.put("asOf", asOf);
        data.putAll(extra);
        if (items.isEmpty() && !more && "COMPLETE".equals(coverage))
            return json(Map.of("status", "NO_DATA", "data", data));
        return success(data);
    }

    String success(Object data) { return json(Map.of("status", "SUCCESS", "data", data)); }
    String invalid() { return "{\"status\":\"INVALID_ARGUMENTS\",\"message\":\"Tool arguments or cursor are invalid. Restart without a cursor if needed.\"}"; }
    String forbidden() { return "{\"status\":\"FORBIDDEN\",\"message\":\"Learner context is unavailable.\"}"; }
    String error(String code) { return json(Map.of("status", "ERROR", "code", code,
            "message", "Business context is unavailable or exceeds safe limits.")); }

    String json(Object value) {
        String result = mapper.writeValueAsString(value);
        // No row-level cursor is available; dropping rows would skip them on continuation.
        if (result.getBytes(StandardCharsets.UTF_8).length > 12000)
            return "{\"status\":\"ERROR\",\"code\":\"RESULT_LIMIT\",\"message\":\"Result is too large. Restart with a smaller limit.\"}";
        return result;
    }

    static Map<String, Object> fields(Object... entries) {
        var result = new LinkedHashMap<String, Object>();
        for (int i = 0; i < entries.length; i += 2) result.put((String) entries[i], entries[i + 1]);
        return result;
    }

    static String bounded(String value, int max) {
        if (value == null || value.length() <= max) return value;
        int end = max;
        if (Character.isHighSurrogate(value.charAt(end - 1))) end--;
        return value.substring(0, end);
    }
}
