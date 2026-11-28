package fit.iuh.engolearn.subscription.service.impl;

import fit.iuh.engolearn.subscription.dto.response.SubscriptionProductServiceResponse;
import java.util.List;

final class SubscriptionProductRegistry {
    private static final List<SubscriptionProductServiceResponse> SERVICES = List.of(
            new SubscriptionProductServiceResponse("SPEAKING_DURATION", "Luyện nói với AI mỗi ngày",
                    "Thời lượng luyện nói với AI được cấp theo chu kỳ.", "SPEAKING_DURATION", "SECOND", "DAILY"),
            new SubscriptionProductServiceResponse("PROFICIENCY_CHECK_ATTEMPT", "Đánh giá trình độ tiếng Anh",
                    "Lượt đánh giá trình độ được cấp theo chu kỳ.", "PROFICIENCY_CHECK_ATTEMPT", "ATTEMPT", "MONTHLY"),
            new SubscriptionProductServiceResponse("AI_COMPANION", "Trò chuyện với AI mỗi ngày",
                    "Lượt sử dụng AI Companion được tính theo token trong ngày.",
                    "AI_COMPANION_TOKEN_USAGE", "TOKEN", "DAILY"),
            new SubscriptionProductServiceResponse("ASSESSMENT_FULL_ANALYSIS", "Xem phân tích đầy đủ",
                    "Xem đầy đủ phân tích của kết quả đánh giá.", null, null, null));

    private SubscriptionProductRegistry() {}

    static List<SubscriptionProductServiceResponse> services() { return SERVICES; }
    static SubscriptionProductServiceResponse service(String code) {
        return SERVICES.stream().filter(service -> service.code().equals(code)).findFirst().orElse(null);
    }
}
