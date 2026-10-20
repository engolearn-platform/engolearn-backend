package fit.iuh.engolearn.topic.repository;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.topic.model.Topic;
import org.springframework.lang.Nullable;

import java.util.List;

interface TopicPageQueries {
    List<Topic> findPublishedPage(@Nullable CefrLevel cefrLevel,
                                 @Nullable String keyword,
                                 TopicRepository.SortKey sortKey,
                                 @Nullable Integer cursorSortKey,
                                 @Nullable String cursorId,
                                 int limit);
}
