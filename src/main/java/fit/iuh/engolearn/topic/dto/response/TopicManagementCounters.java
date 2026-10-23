package fit.iuh.engolearn.topic.dto.response;

/**
 * E4 counters — computed over the <em>entire managed set</em>, not the filtered
 * set (design record F05 §4.6, {@code DEC-10}).
 */
public record TopicManagementCounters(long total, long published, long draft) {
}
