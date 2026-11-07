package fit.iuh.engolearn.placement.repository;

import fit.iuh.engolearn.placement.model.PlacementQuestion;
import fit.iuh.engolearn.placement.model.enums.PlacementLevel;
import fit.iuh.engolearn.placement.model.enums.PlacementSkill;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PlacementQuestionRepository {
    Optional<PlacementQuestion> findById(String id);
    List<PlacementQuestion> findAllById(Collection<String> ids);
    List<PlacementQuestion> findActiveBySkillAndLevel(PlacementSkill skill, PlacementLevel level);
    List<PlacementQuestion> findActiveByIds(Collection<String> ids);
}
