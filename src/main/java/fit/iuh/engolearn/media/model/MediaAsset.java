package fit.iuh.engolearn.media.model;

import fit.iuh.engolearn.media.contract.enums.MediaAccessPolicy;
import fit.iuh.engolearn.media.model.vo.MediaStorageMetadataVo;
import fit.iuh.engolearn.shared.BaseDocument;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/** EngoLearn-owned metadata for one uploaded media asset. */
@Document("media_assets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class MediaAsset extends BaseDocument {

    @Field("purpose")
    private String purpose;

    @Field("access_policy")
    private MediaAccessPolicy accessPolicy;

    @Field("original_filename")
    private String originalFilename;

    @Field("content_type")
    private String contentType;

    @Field("size_bytes")
    private long sizeBytes;

    @Field("storage")
    private MediaStorageMetadataVo storage;

    @Field("created_by")
    private String createdBy;
}
