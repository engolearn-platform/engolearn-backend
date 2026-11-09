package fit.iuh.engolearn.topic.contract.dto;

import java.util.List;

public record SpeakingItemDto(String itemId, String itemVersionId, String title,
                              String description, List<String> vocabulary,
                              List<String> expressions) {}
