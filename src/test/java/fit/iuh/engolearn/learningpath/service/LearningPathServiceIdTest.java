package fit.iuh.engolearn.learningpath.service;

import fit.iuh.engolearn.learningpath.model.LearningPath;
import fit.iuh.engolearn.learningpath.model.enums.LearningPathStatus;
import fit.iuh.engolearn.learningpath.repository.LearningPathRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LearningPathServiceIdTest {

    @Test
    void detailAndSearchPreserveIdAndSortDistinctResultsByCreationTime() {
        LearningPathRepository repository = mock(LearningPathRepository.class);
        LearningPathService service = new LearningPathService(repository);
        ObjectId userId = new ObjectId();
        ObjectId olderId = new ObjectId();
        LearningPath older = path(olderId, "2026-10-27T03:00:00Z");
        LearningPath newer = path(new ObjectId(), "2026-10-28T03:00:00Z");
        when(repository.findByIdAndUserId(olderId, userId)).thenReturn(Optional.of(older));
        when(repository.findByUserIdAndStatusInAndMicroSkillsMicroSkillContainingIgnoreCaseOrderByCreatedAtDesc(
                eq(userId), anyList(), eq("27/10/2026"))).thenReturn(List.of(older, newer));
        when(repository.findByUserIdAndStatusInAndCreatedDateOrderByCreatedAtDesc(
                eq(userId), anyList(), eq("2026-10-27"))).thenReturn(List.of(older));
        when(repository.countByUserIdAndStatusIn(eq(userId), anyList())).thenReturn(2L);
        when(repository.countByUserIdAndStatus(eq(userId), any())).thenReturn(0L);

        assertThat(service.getDetail(olderId.toHexString(), userId.toHexString()).id())
                .isEqualTo(olderId.toHexString());
        assertThat(service.search(userId.toHexString(), "27/10/2026").items())
                .extracting(item -> item.id())
                .containsExactly(newer.getId(), older.getId());
    }

    private LearningPath path(ObjectId id, String createdAt) {
        LearningPath path = new LearningPath();
        path.setId(id.toHexString());
        path.setCreatedAt(Instant.parse(createdAt));
        path.setStatus(LearningPathStatus.IN_PROGRESS);
        return path;
    }
}
