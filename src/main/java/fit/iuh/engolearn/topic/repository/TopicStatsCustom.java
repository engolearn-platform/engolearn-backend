package fit.iuh.engolearn.topic.repository;

import java.util.Collection;
import java.util.Map;

/**
 * Read-only per-topic item aggregates (design record F05 §3.6): one
 * aggregation per request, no N+1 queries, no full-document loads.
 * Statistics are derived from each item's latest version, excluding only HIDDEN.
 */
public interface TopicStatsCustom {

    /** Stats for a single topic; {@link TopicItemStats#EMPTY} when it has no counted items. */
    TopicItemStats statsByTopicId(String topicId);

    /** Stats for many topics at once; topics without counted items are absent from the map. */
    Map<String, TopicItemStats> statsByTopicIds(Collection<String> topicIds);
}
