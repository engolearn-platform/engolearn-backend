package fit.iuh.engolearn.topic.model;

import fit.iuh.engolearn.shared.BaseDocument;
import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.topic.model.enums.TopicCategory;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

/**
 * Topic document.
 *
 * <p>Index (design record F05 §3.6): {@code updated_at} on {@code topics} for
 * the management list's fixed {@code updatedAt DESC} sort. Declared as a
 * single-field class-level {@code @CompoundIndex} named {@code updated_at}
 * because {@code updatedAt} is inherited from {@code BaseDocument} (which is
 * frozen) and Spring Data's {@code @Indexed} can only annotate fields.</p>
 *
 * <p>F05 never writes {@code durationMinutes} (computed on read) and never
 * writes {@code tags}/{@code orderIndex}/{@code latestBetaVersion}; F05 writes
 * {@code latestPublishedVersion} only never (publish is F09).</p>
 */
@Document("topics")
@CompoundIndex(name = "updated_at", def = "{'updated_at': 1}")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class Topic extends BaseDocument {
    @Field("latest_published_version")
    private String latestPublishedVersion;

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
