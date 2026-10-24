package fit.iuh.engolearn.topic.repository;

/** Counts and sums duration minutes over each item's latest non-HIDDEN version. */
public record TopicItemStats(long itemCount, long totalDurationMinutes) {

    public static final TopicItemStats EMPTY = new TopicItemStats(0L, 0L);
}
