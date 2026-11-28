package fit.iuh.engolearn.placement.repository;

import fit.iuh.engolearn.placement.model.PlacementQuestion;
import fit.iuh.engolearn.placement.model.PlacementTestSet;
import fit.iuh.engolearn.placement.model.enums.PlacementTestSetStatus;
import fit.iuh.engolearn.placement.model.vo.PlacementSnapshotItemVo;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PlacementAdminTestSetRepository {
    long countAll();
    Page list(String search, PlacementTestSetStatus status, int page, int size);
    Optional<PlacementTestSet> findById(String id);
    PlacementTestSet insert(PlacementTestSet set);
    int latestVersion(String code);
    boolean hasEditableDraft(String code);
    boolean hasAttempts(PlacementTestSet set);
    boolean guardActiveQuestion(PlacementQuestion question);
    Optional<PlacementTestSet> saveDraft(String id, long revision, String name, List<String> questionIds, Instant now);
    Optional<PlacementTestSet> publish(String id, long revision, String name, List<String> questionIds,
                                      List<PlacementSnapshotItemVo> items, Instant now);
    Optional<PlacementTestSet> archive(String id, long revision, Instant now);
    boolean deleteDraft(String id, long revision);

    record Page(List<PlacementTestSet> items, long total) {}
}
