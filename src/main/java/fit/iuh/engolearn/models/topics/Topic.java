package fit.iuh.engolearn.models.topics;

import fit.iuh.engolearn.models.shared.BaseDocument;
import fit.iuh.engolearn.models.shared.enums.CefrLevel;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("topics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class Topic extends BaseDocument {
    @Field("latestPublishedVersion")
    private Integer latestPublishedVersion;

    @Field("title")
    private String title;

    @Field("description")
    private String description;

    @Field("cefr_level")
    private CefrLevel cefrLevel;

    @Field("order_index")
    private Integer orderIndex;

    @Field("total_words")
    private Integer totalWords;

    @Field("duration_minutes")
    private Integer durationMinutes;
}