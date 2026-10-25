package fit.iuh.engolearn.topic.service;

import fit.iuh.engolearn.topic.dto.request.TopicCreateRequest;
import fit.iuh.engolearn.topic.dto.request.TopicManagementListQuery;
import fit.iuh.engolearn.topic.dto.request.TopicUpdateRequest;
import fit.iuh.engolearn.topic.dto.response.TopicManagementListResponse;
import fit.iuh.engolearn.topic.dto.response.TopicManagementResponse;

public interface TopicManagementService {

    TopicManagementResponse create(TopicCreateRequest request);

    TopicManagementResponse get(String id);

    TopicManagementResponse update(String id, TopicUpdateRequest request);

    TopicManagementListResponse list(TopicManagementListQuery query);
}
