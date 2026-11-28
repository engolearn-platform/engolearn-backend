package fit.iuh.engolearn.microskill.dto.response;
import java.util.List;
public record MicroSkillListResponse(List<MicroSkillResponse> items) {
    public MicroSkillListResponse { items = List.copyOf(items); }
}
