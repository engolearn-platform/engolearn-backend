package fit.iuh.engolearn.topic.contract.dto;

import fit.iuh.engolearn.shared.enums.CefrLevel;

public record TopicDiscoveryQueryDto(String keyword, CefrLevel level, int limit, String cursor) {}
