package fit.iuh.engolearn.subscription.repository;

import fit.iuh.engolearn.subscription.model.SubscriptionProduct;
import fit.iuh.engolearn.subscription.model.SubscriptionProductPublicationReceipt;
import fit.iuh.engolearn.subscription.model.SubscriptionProductVersion;
import fit.iuh.engolearn.subscription.model.vo.ProductContentVo;
import java.util.List;

public interface SubscriptionProductRepository {
    SubscriptionProduct findProduct(String planCode);
    List<SubscriptionProduct> findProducts();
    SubscriptionProductVersion findVersion(String planCode, long version);
    List<SubscriptionProductVersion> findVersions(String planCode);
    SubscriptionProductPublicationReceipt findPublicationReceipt(String actor, String key);
    SubscriptionProduct insertProduct(SubscriptionProduct product);
    SubscriptionProductVersion insertVersion(SubscriptionProductVersion version);
    SubscriptionProductPublicationReceipt insertPublicationReceipt(SubscriptionProductPublicationReceipt receipt);
    boolean saveDraft(String planCode, long expectedRevision, ProductContentVo content);
    boolean publish(String planCode, Long expectedVersion, long expectedRevision, long version);
    boolean setVisibility(String planCode, boolean hidden);
    boolean setSaleStatus(String planCode, boolean stopped);
    boolean archive(String planCode);
    boolean deleteDraft(String planCode, long expectedRevision);
    boolean reservePurchase(String planCode, long expectedVersion);
    boolean replaceLegacySchedule(String planCode, Long expectedCurrentVersion, Long expectedScheduleVersion,
            long nextScheduleVersion);
    boolean fenceLegacySchedule(String planCode, Long expectedCurrentVersion, Long expectedScheduleVersion);
    boolean applyLegacySchedule(String planCode, Long expectedCurrentVersion, long scheduleVersion);
    boolean setLegacyScheduleStatus(String planCode, long version, String status);
}
