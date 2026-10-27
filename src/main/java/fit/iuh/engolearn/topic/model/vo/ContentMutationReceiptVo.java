package fit.iuh.engolearn.topic.model.vo;

import fit.iuh.engolearn.topic.model.enums.LearningStage;
import java.time.Instant;

public record ContentMutationReceiptVo(String key, String fingerprint, LearningStage stage, int itemIndex, long revision, Instant accessedAt) {}
