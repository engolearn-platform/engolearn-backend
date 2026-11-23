package fit.iuh.engolearn.media.repository;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.media.contract.enums.MediaAccessPolicy;
import fit.iuh.engolearn.media.model.MediaAsset;
import fit.iuh.engolearn.media.model.enums.MediaDeliveryType;
import fit.iuh.engolearn.media.model.enums.MediaProvider;
import fit.iuh.engolearn.media.model.enums.MediaResourceType;
import fit.iuh.engolearn.media.model.vo.MediaStorageMetadataVo;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfDockerAvailable
class MediaAssetRepositoryIntegrationTest extends AbstractMongoIntegrationTest {

    @Autowired
    private MediaAssetRepository repository;

    @Test
    void persistsMediaMetadataWithSnakeCaseFieldsAndProviderDetails() {
        MediaAsset asset = MediaAsset.builder()
                .id("media-integration-test")
                .purpose("LESSON_AUDIO")
                .accessPolicy(MediaAccessPolicy.PROTECTED)
                .originalFilename("lesson.mp3")
                .contentType("audio/mpeg")
                .sizeBytes(2048)
                .storage(MediaStorageMetadataVo.builder()
                        .provider(MediaProvider.CLOUDINARY)
                        .assetId("provider-asset-id")
                        .publicId("engolearn/media/integration-test")
                        .resourceType(MediaResourceType.VIDEO)
                        .deliveryType(MediaDeliveryType.AUTHENTICATED)
                        .format("mp3")
                        .version(123L)
                        .build())
                .createdBy("admin-test")
                .build();

        repository.save(asset);

        Document stored = mongoTemplate.getCollection("media_assets")
                .find(new Document("_id", "media-integration-test")).first();
        assertThat(stored).isNotNull();
        assertThat(stored.getString("purpose")).isEqualTo("LESSON_AUDIO");
        assertThat(stored.getString("access_policy")).isEqualTo("PROTECTED");
        assertThat(stored.getString("content_type")).isEqualTo("audio/mpeg");
        assertThat(stored.get("size_bytes")).isEqualTo(2048L);
        assertThat(stored.get("storage", Document.class).getString("public_id"))
                .isEqualTo("engolearn/media/integration-test");
        assertThat(stored.get("storage", Document.class).getString("resource_type")).isEqualTo("VIDEO");
        assertThat(repository.findById("media-integration-test")).isPresent();
    }
}
