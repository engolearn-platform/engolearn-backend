package fit.iuh.engolearn.topic.dto.response;

import fit.iuh.engolearn.shared.enums.CefrLevel;

public record TopicViewerResponse(String kind, String learnerId, String displayName, CefrLevel cefrLevel) {}
