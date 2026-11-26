package fit.iuh.engolearn.media.model.vo;

import fit.iuh.engolearn.media.model.enums.MediaDeliveryType;
import fit.iuh.engolearn.media.model.enums.MediaProvider;
import fit.iuh.engolearn.media.model.enums.MediaResourceType;
import fit.iuh.engolearn.shared.EmbeddedDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

/** Provider identifiers needed to deliver or compensate an uploaded asset. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
public class MediaStorageMetadataVo implements EmbeddedDocument {

    @Field("provider")
    private MediaProvider provider;

    @Field("asset_id")
    private String assetId;

    @Field("public_id")
    private String publicId;

    @Field("resource_type")
    private MediaResourceType resourceType;

    @Field("delivery_type")
    private MediaDeliveryType deliveryType;

    @Field("format")
    private String format;

    @Field("version")
    private Long version;
}
