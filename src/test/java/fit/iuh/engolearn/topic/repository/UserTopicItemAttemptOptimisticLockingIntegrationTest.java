package fit.iuh.engolearn.topic.repository;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.topic.model.UserTopicItemAttempt;
import fit.iuh.engolearn.topic.model.enums.AttemptStatus;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Optimistic-locking state machine on {@code UserTopicItemAttempt.version}: a successful save
 * advances the version by exactly one, a save from a stale copy is rejected and leaves the
 * persisted document untouched.
 */
@EnabledIfDockerAvailable
class UserTopicItemAttemptOptimisticLockingIntegrationTest extends AbstractMongoIntegrationTest {

    private static final Instant STARTED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-01-01T00:05:00Z");
    private static final Instant ABANDONED_AT = Instant.parse("2026-01-01T00:10:00Z");

    @Autowired
    private UserTopicItemAttemptRepository userTopicItemAttemptRepository;

    @Test
    void staleAttemptCopyIsRejectedWhileSuccessfulSaveAdvancesVersionByExactlyOne() {
        UserTopicItemAttempt inserted = userTopicItemAttemptRepository.save(newAttempt());
        Long insertVersion = inserted.getVersion();
        assertThat(insertVersion).isZero();

        UserTopicItemAttempt copyA = userTopicItemAttemptRepository.findById(inserted.getId()).orElseThrow();
        UserTopicItemAttempt copyB = userTopicItemAttemptRepository.findById(inserted.getId()).orElseThrow();
        assertThat(copyA.getVersion()).isEqualTo(copyB.getVersion()).isEqualTo(insertVersion);

        copyA.setStatus(AttemptStatus.COMPLETED);
        copyA.setCompletedAt(COMPLETED_AT);
        UserTopicItemAttempt savedCopyA = userTopicItemAttemptRepository.save(copyA);
        assertThat(savedCopyA.getVersion()).isEqualTo(insertVersion + 1);

        copyB.setStatus(AttemptStatus.ABANDONED);
        copyB.setLastAccessedAt(ABANDONED_AT);
        assertThat(copyB.getVersion()).isEqualTo(insertVersion);
        assertThatThrownBy(() -> userTopicItemAttemptRepository.save(copyB))
                .isInstanceOf(OptimisticLockingFailureException.class);

        UserTopicItemAttempt persisted = userTopicItemAttemptRepository.findById(inserted.getId()).orElseThrow();
        assertThat(persisted.getVersion()).isEqualTo(insertVersion + 1);
        assertThat(persisted.getStatus()).isEqualTo(AttemptStatus.COMPLETED);
        assertThat(persisted.getCompletedAt()).isEqualTo(COMPLETED_AT);
        assertThat(persisted.getLastAccessedAt()).isEqualTo(STARTED_AT);
    }

    private UserTopicItemAttempt newAttempt() {
        // OBJECT_ID-typed fields only round-trip 24-hex values, so ids come from ObjectId.
        return UserTopicItemAttempt.builder()
                .userTopicAttemptId(new ObjectId().toHexString())
                .userId("user-mock-001")
                .topicId(new ObjectId().toHexString())
                .topicItemId(new ObjectId().toHexString())
                .topicItemVersionId(new ObjectId().toHexString())
                .status(AttemptStatus.IN_PROGRESS)
                .startedAt(STARTED_AT)
                .lastAccessedAt(STARTED_AT)
                .build();
    }
}
