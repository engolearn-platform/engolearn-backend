package fit.iuh.engolearn.placement.repository;

import fit.iuh.engolearn.placement.model.PlacementQuestion;
import fit.iuh.engolearn.placement.model.enums.PlacementLevel;
import fit.iuh.engolearn.placement.model.enums.PlacementQuestionStatus;
import fit.iuh.engolearn.placement.model.enums.PlacementSkill;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PlacementQuestionRepository {
    Optional<PlacementQuestion> findById(String id);
    List<PlacementQuestion> findAllById(Collection<String> ids);
    List<PlacementQuestion> findActiveBySkillAndLevel(PlacementSkill skill, PlacementLevel level);
    List<PlacementQuestion> findActiveByIds(Collection<String> ids);

    AdminPage findAdminPage(String search, PlacementSkill skill, PlacementLevel level,
                           PlacementQuestionStatus status, int page, int size);
    Map<String, String> findListeningAudioTitles();
    PlacementQuestion insert(PlacementQuestion question);
    void insertAll(List<PlacementQuestion> questions);
    Optional<PlacementQuestion> updateIfCurrent(PlacementQuestion question, long revision);
    Optional<PlacementQuestion> updateStatusIfCurrent(PlacementQuestion question, long revision);

    record AdminPage(List<PlacementQuestion> items, long total) {}
}
