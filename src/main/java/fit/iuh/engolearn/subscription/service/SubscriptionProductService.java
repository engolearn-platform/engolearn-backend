package fit.iuh.engolearn.subscription.service;

import fit.iuh.engolearn.subscription.dto.request.*;
import fit.iuh.engolearn.subscription.dto.response.*;
import fit.iuh.engolearn.subscription.model.vo.CurrentBenefitsVo;
import fit.iuh.engolearn.subscription.model.vo.PlanBillingOptionVo;
import fit.iuh.engolearn.subscription.model.vo.QuotaPolicySnapshotVo;
import java.time.Instant;
import java.util.List;

public interface SubscriptionProductService {
    SubscriptionProductAdminListResponse adminList();
    SubscriptionProductDetailResponse adminDetail(String planCode);
    SubscriptionProductVersionResponse versionDetail(String planCode, long version);
    SubscriptionProductDetailResponse create(String planCode);
    SubscriptionProductDetailResponse saveDraft(String planCode, SaveSubscriptionProductDraftRequest request);
    SubscriptionProductPreviewResponse preview(String planCode, PreviewSubscriptionProductRequest request);
    SubscriptionProductDetailResponse publish(String actor, String key, String planCode,
            PublishSubscriptionProductRequest request);
    SubscriptionProductDetailResponse rescheduleLegacyPremium(RescheduleLegacyPremiumRequest request);
    SubscriptionProductDetailResponse cancelLegacyPremiumSchedule(CancelLegacyPremiumScheduleRequest request);
    SubscriptionProductDetailResponse visibility(String planCode, SetProductVisibilityRequest request);
    SubscriptionProductDetailResponse saleStatus(String planCode, SetProductSaleStatusRequest request);
    SubscriptionProductDetailResponse archive(String planCode);
    void deleteDraft(String planCode);
    List<PlanCardResponse> publicPlans();
    PlanCardResponse publicPlan(String planCode);
    PurchaseSelection reserveForPurchase(String planCode, long expectedVersion, String billingPeriod);

    record PurchaseSelection(PlanCardResponse card, CurrentBenefitsVo benefits, QuotaPolicySnapshotVo policy,
            PlanBillingOptionVo selectedPrice, long version, Instant effectiveAt) {}
}
