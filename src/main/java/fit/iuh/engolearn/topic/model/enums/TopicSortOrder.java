package fit.iuh.engolearn.topic.model.enums;

import java.util.Locale;
import java.util.Optional;

public enum TopicSortOrder {
    RECOMMENDED("recommended"),
    DURATION("duration");

    private final String apiValue;

    TopicSortOrder(String apiValue) {
        this.apiValue = apiValue;
    }

    public String apiValue() {
        return apiValue;
    }

    public static Optional<TopicSortOrder> fromApiValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.of(RECOMMENDED);
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (TopicSortOrder sortOrder : values()) {
            if (sortOrder.apiValue.equals(normalized)) {
                return Optional.of(sortOrder);
            }
        }
        return Optional.empty();
    }
}