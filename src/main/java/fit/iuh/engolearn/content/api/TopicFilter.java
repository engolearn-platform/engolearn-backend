package fit.iuh.engolearn.content.api;

import fit.iuh.engolearn.shared.model.CefrLevel;

public record TopicFilter(CefrLevel level, String category) {

    public static TopicFilter empty() {
        return new TopicFilter(null, null);
    }
}
