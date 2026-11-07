package fit.iuh.engolearn.learningpath.service;

import fit.iuh.engolearn.learningpath.model.LearningPath;
import fit.iuh.engolearn.learningpath.model.enums.LearningPathStatus;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathSource;
import fit.iuh.engolearn.learningpath.dto.request.LearningPathListStatus;
import fit.iuh.engolearn.learningpath.repository.LearningPathRepository;
import fit.iuh.engolearn.assessment.contract.AssessmentResults;
import fit.iuh.engolearn.learningpath.service.impl.LearningPathServiceImpl;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class LearningPathServiceIdTest {

    @Test
    void detailAndSearchPreserveIdAndSortDistinctResultsByCreationTime() {
        LearningPathRepository repository = mock(LearningPathRepository.class);
        LearningPathService service = new LearningPathServiceImpl(repository, mock(AssessmentResults.class));
        String userId = new ObjectId().toHexString();
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

        assertThat(service.getDetail(olderId.toHexString(), userId).id())
                .isEqualTo(olderId.toHexString());
        assertThat(service.search(userId, "27/10/2026").items())
                .extracting(item -> item.id())
                .containsExactly(newer.getId(), older.getId());
    }

    @Test
    void listResolvesDistinctSourceIdsInOneOwnerScopedBatchAndKeepsTheStoredSource() {
        LearningPathRepository repository = mock(LearningPathRepository.class);
        AssessmentResults periodicTests = mock(AssessmentResults.class);
        LearningPathService service = new LearningPathServiceImpl(repository, periodicTests);
        String userId = new ObjectId().toHexString();
        String attemptId = "native-assessment-id";
        LearningPath first = path(new ObjectId(), "2026-11-01T03:00:00Z");
        LearningPath second = path(new ObjectId(), "2026-11-02T03:00:00Z");
        LearningPath legacy = path(new ObjectId(), "2026-11-03T03:00:00Z");
        LearningPathSource source = new LearningPathSource();
        source.setAssessmentAttemptId(attemptId);
        first.setSource(source);
        second.setSource(source);
        when(repository.findByUserIdAndStatusInOrderByCreatedAtDesc(eq(userId), anyList()))
                .thenReturn(List.of(first, second, legacy));
        when(periodicTests.findCompletedAttemptIds(userId, java.util.Set.of(attemptId)))
                .thenReturn(java.util.Set.of(attemptId));

        assertThat(service.getByUserId(userId, LearningPathListStatus.ACTIVE).items())
                .extracting(item -> item.sourceAssessmentAttemptId())
                .containsExactly("native-assessment-id", "native-assessment-id", null);
        verify(periodicTests).findCompletedAttemptIds(userId, java.util.Set.of(attemptId));
        assertThat(first.getSource().getAssessmentAttemptId()).isEqualTo(attemptId);
    }

    private LearningPath path(ObjectId id, String createdAt) {
        LearningPath path = new LearningPath();
        path.setId(id.toHexString());
        path.setCreatedAt(Instant.parse(createdAt));
        path.setStatus(LearningPathStatus.IN_PROGRESS);
        return path;
    }
}
