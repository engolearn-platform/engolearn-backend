package fit.iuh.engolearn.models.topics;

import fit.iuh.engolearn.models.shared.BaseDocument;
import fit.iuh.engolearn.models.shared.enums.CefrLevel;
import fit.iuh.engolearn.models.topics.enums.TopicCategory;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

@Document("topics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class Topic extends BaseDocument {
    @Field("latest_published_version")
    private Integer latestPublishedVersion;

    @Field("latest_beta_version")
    private Integer latestBetaVersion;

    @Field("title_en")
    private String titleEn;

    @Field("title_vi")
    private String titleVi;

    @Field("description")
    private String description;

    @Field("cefr_level")
    private CefrLevel cefrLevel;

    @Field("category")
    private TopicCategory category;

    @Field("order_index")
    private Integer orderIndex;

    @Field("duration_minutes")
    private Integer durationMinutes;

    @Field("cover_url")
    private String coverUrl;

    @Field("tags")
    private List<String> tags;
}
