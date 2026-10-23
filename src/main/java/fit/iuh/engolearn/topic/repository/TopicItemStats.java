package fit.iuh.engolearn.topic.repository;

/**
 * Per-topic item aggregates over the non-ARCHIVED item set (design record F05 §3.5):
 * {@code itemCount} = number of counted items, {@code totalDurationSec} =
 * Σ {@code context.durationSec} with null durations coalesced to 0.
 *
 * <p>{@code durationMinutes} is derived in the service as
 * {@code ceil(totalDurationSec / 60.0)} and never persisted.</p>
 */
public record TopicItemStats(long itemCount, long totalDurationSec) {

    public static final TopicItemStats EMPTY = new TopicItemStats(0L, 0L);
}
