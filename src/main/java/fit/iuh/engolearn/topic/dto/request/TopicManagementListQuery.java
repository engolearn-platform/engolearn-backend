package fit.iuh.engolearn.topic.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TopicManagementListQuery(
        @Size(max = 200) String search,
        @Pattern(regexp = "A1|A2|B1|B2") String cefr,
        @Pattern(regexp = "DINING|TRAVEL|BUSINESS|DAILY|HEALTH") String category,
        @Pattern(regexp = "draft|published") String status,
        @Min(0) int page,
        @Min(1) @Max(100) int size) {}
