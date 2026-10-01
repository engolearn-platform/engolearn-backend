package fit.iuh.engolearn.content.api;

import fit.iuh.engolearn.shared.model.ContentStatus;
import java.util.List;

public record TopicItemDetailView(
        String topicItemId,
        Integer version,
        String title,
        String description,
        Integer order,
        ContentStatus status,
        ItemContextView context,
        List<ItemWordView> words,
        ExpressionView expressions,
        List<QuizModuleView> quizzes) {
}
