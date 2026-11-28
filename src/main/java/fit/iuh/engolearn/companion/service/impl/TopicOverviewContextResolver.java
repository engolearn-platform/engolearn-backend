package fit.iuh.engolearn.companion.service.impl;

import fit.iuh.engolearn.companion.dto.request.ScreenContextReferenceRequest;
import fit.iuh.engolearn.companion.model.enums.ScreenContextType;
import fit.iuh.engolearn.companion.service.ScreenContextResolver;
import fit.iuh.engolearn.topic.contract.TopicContextLookup;
import fit.iuh.engolearn.topic.contract.dto.TopicOverviewContextDto;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TopicOverviewContextResolver implements ScreenContextResolver<TopicOverviewContextDto> {
    private final TopicContextLookup topics;

    @Override public ScreenContextType supportedType() { return ScreenContextType.TOPIC_OVERVIEW; }

    @Override public boolean accepts(ScreenContextReferenceRequest ref) {
        return ref.topicId() != null && !ref.topicId().isBlank() && ref.topicId().length() <= 128
                && ref.attemptId() == null && ref.stage() == null && ref.itemIndex() == null;
    }

    @Override public Optional<TopicOverviewContextDto> resolve(String learnerId, ScreenContextReferenceRequest ref) {
        return topics.getOverview(learnerId, ref.topicId());
    }
}
