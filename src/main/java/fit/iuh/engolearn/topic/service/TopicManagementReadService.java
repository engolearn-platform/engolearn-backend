package fit.iuh.engolearn.topic.service;

import fit.iuh.engolearn.topic.dto.response.TopicManagementItemDetail;
import fit.iuh.engolearn.topic.dto.response.TopicManagementItemSummary;
import fit.iuh.engolearn.topic.dto.response.TopicManagementListResponse;
import fit.iuh.engolearn.topic.dto.response.TopicManagementResponse;
import fit.iuh.engolearn.topic.dto.request.TopicManagementListQuery;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
public interface TopicManagementReadService {
    TopicManagementListResponse list(@Valid TopicManagementListQuery query);

    TopicManagementResponse topic(String topicId);

    List<TopicManagementItemSummary> items(String topicId);

    TopicManagementItemDetail item(String topicId, String itemId);

    TopicManagementItemDetail publishedItem(String topicId, String itemId);

    List<TopicItemVersion> speakingAuthoringItems(String topicId, String itemId);
}
