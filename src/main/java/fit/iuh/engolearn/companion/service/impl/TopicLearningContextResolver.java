package fit.iuh.engolearn.companion.service.impl;

import fit.iuh.engolearn.companion.dto.request.ScreenContextReferenceRequest;
import fit.iuh.engolearn.companion.model.enums.ScreenContextType;
import fit.iuh.engolearn.companion.service.ScreenContextResolver;
import fit.iuh.engolearn.topic.contract.TopicContextLookup;
import fit.iuh.engolearn.topic.contract.dto.TopicLearningContextDto;
import fit.iuh.engolearn.topic.contract.dto.TopicLearningPositionDto;
import fit.iuh.engolearn.topic.contract.enums.TopicContextStage;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TopicLearningContextResolver implements ScreenContextResolver<TopicLearningContextDto<?>> {
    private final TopicContextLookup topics;

    @Override public ScreenContextType supportedType() { return ScreenContextType.TOPIC_LEARNING_POSITION; }

    @Override public boolean accepts(ScreenContextReferenceRequest ref) {
        if (ref.topicId() != null || ref.attemptId() == null || ref.attemptId().isBlank() || ref.attemptId().length() > 128
                || ref.stage() == null || ref.itemIndex() == null || ref.itemIndex() < 0) return false;
        try {
            return TopicContextStage.valueOf(ref.stage()) != TopicContextStage.CONTEXT || ref.itemIndex() == 0;
        } catch (IllegalArgumentException invalid) { return false; }
    }

    @Override public Optional<TopicLearningContextDto<?>> resolve(String learnerId, ScreenContextReferenceRequest ref) {
        return topics.getLearningContext(learnerId, ref.attemptId(),
                new TopicLearningPositionDto(TopicContextStage.valueOf(ref.stage()), ref.itemIndex()));
    }
}
