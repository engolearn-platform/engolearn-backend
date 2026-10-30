package fit.iuh.engolearn.topic.service.impl;

import fit.iuh.engolearn.topic.service.*;
import lombok.RequiredArgsConstructor;

import fit.iuh.engolearn.topic.dto.request.CreateLocalLearnerRequest;
import fit.iuh.engolearn.topic.dto.response.LocalLearnerResponse;
import fit.iuh.engolearn.topic.model.TopicLearnerProfile;
import fit.iuh.engolearn.topic.repository.TopicLearnerProfileRepository;
import org.springframework.stereotype.Service;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class LocalLearnerServiceImpl implements LocalLearnerService {
    private final TopicLearnerProfileRepository profiles;

    @Override
    public LocalLearnerResponse create(CreateLocalLearnerRequest request) {
        TopicLearnerProfile saved = profiles.insert(TopicLearnerProfile.builder()
                .displayName(request.displayName().trim())
                .cefrLevel(request.cefrLevel())
                .createdAt(Instant.now())
                .build());
        return new LocalLearnerResponse(saved.getId(), saved.getDisplayName(), saved.getCefrLevel());
    }
}
