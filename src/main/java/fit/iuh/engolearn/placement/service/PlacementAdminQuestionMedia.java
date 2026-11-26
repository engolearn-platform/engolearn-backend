package fit.iuh.engolearn.placement.service;

import fit.iuh.engolearn.placement.dto.response.PlacementAdminQuestionMediaResponse;
import fit.iuh.engolearn.placement.dto.response.PlacementAdminQuestionMediaResponse.ItemResponse;
import fit.iuh.engolearn.placement.model.PlacementQuestion;
import java.util.Map;

public interface PlacementAdminQuestionMedia {
    String resolve(PlacementQuestion question);
    void requirePlayable(PlacementQuestion question);
    ItemResponse requireSelection(String url);
    PlacementAdminQuestionMediaResponse catalog(Map<String, String> titlesByHash);
}
