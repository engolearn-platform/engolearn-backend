package fit.iuh.engolearn.topic.service;

import fit.iuh.engolearn.topic.dto.request.CreateLocalLearnerRequest;
import fit.iuh.engolearn.topic.dto.response.LocalLearnerResponse;

public interface LocalLearnerService {
    LocalLearnerResponse create(CreateLocalLearnerRequest request);
}
