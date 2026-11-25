package fit.iuh.engolearn.topic.contract.dto;

public record TopicSpeakingResourceDto(String id, Type type, String textEn) {
    public enum Type { VOCABULARY, EXPRESSION }
}
