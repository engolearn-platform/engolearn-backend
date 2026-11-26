package fit.iuh.engolearn.topic.contract.dto;

public record TopicSpeakingResourceDto(String id, Type type, String textEn, String itemId, String context) {
    public TopicSpeakingResourceDto(String id, Type type, String textEn) {
        this(id, type, textEn, null, null);
    }

    public enum Type { VOCABULARY, EXPRESSION }
}
