package fit.iuh.engolearn.topic.repository;

import fit.iuh.engolearn.topic.model.Topic;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Filtered/sorted/paged management search over {@code topics}
 * (design record F05 §3.6 — cannot be expressed as a derived query because of
 * the derived publication state and the OR/AND/regex combination).
 */
public interface TopicSearchCustom {

    Page<Topic> search(TopicManagementSearchCriteria criteria, Pageable pageable);
}
