package fit.iuh.engolearn.media.repository;

import fit.iuh.engolearn.media.model.MediaAsset;
import org.springframework.data.mongodb.repository.MongoRepository;

/** Persistence boundary owned exclusively by the Media feature. */
public interface MediaAssetRepository extends MongoRepository<MediaAsset, String> {
}
