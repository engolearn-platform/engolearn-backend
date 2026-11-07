package fit.iuh.engolearn.placement.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PlacementTimingPolicyVo implements EmbeddedDocument {
    @Field("reading_words_per_minute") private int readingWordsPerMinute;
    @Field("plays_per_audio") private int playsPerAudio;
    @Field("visible_word_limit") private int visibleWordLimit;
    @Field("human_timing_review") private String humanTimingReview;
}
