package fit.iuh.engolearn.topic.contract.dto;

import java.util.List;

public record TopicLearningContextDto<T>(TopicOverviewDto topic, ItemOverviewDto topicItem,
        TopicLearningPositionDto position, T content) {
    public record ItemOverviewDto(String title, String description, String scenarioDescription) {}
    public record DialogueTurnDto(String speaker, String textEn, String textVi) {}
    public record DialogueDto(String promptingQuestion, List<DialogueTurnDto> dialogue, String tips) {
        public DialogueDto { dialogue = List.copyOf(dialogue); }
    }
    public record ExampleDto(String textEn, String textVi) {}
    public record CollocationDto(String text, String meaningVi) {}
    public record VocabularyDto(String word, String partOfSpeech, String meaningVi, String phonetic,
            List<ExampleDto> examples, List<CollocationDto> collocations, String tips) {
        public VocabularyDto { examples = List.copyOf(examples); collocations = List.copyOf(collocations); }
    }
    public record ExpressionExampleDto(String textEn, String textVi, String usageNote) {}
    public record ExpressionsDto(String purposeEn, String purposeVi, List<ExpressionExampleDto> examples, String tips) {
        public ExpressionsDto { examples = List.copyOf(examples); }
    }
}
