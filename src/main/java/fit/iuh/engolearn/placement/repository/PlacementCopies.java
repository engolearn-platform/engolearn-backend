package fit.iuh.engolearn.placement.repository;

import fit.iuh.engolearn.placement.model.PlacementAttempt;
import fit.iuh.engolearn.placement.model.PlacementTestSet;
import fit.iuh.engolearn.placement.model.vo.*;
import java.util.ArrayList;
import java.util.List;

final class PlacementCopies {
    private PlacementCopies() {
    }

    static PlacementAttempt attempt(PlacementAttempt source) {
        return PlacementAttempt.builder()
                .id(source.getId()).createdAt(source.getCreatedAt()).updatedAt(source.getUpdatedAt())
                .userId(source.getUserId()).guestSessionId(source.getGuestSessionId())
                .startSessionKey(source.getStartSessionKey())
                .testSetId(source.getTestSetId()).testSetVersion(source.getTestSetVersion())
                .status(source.getStatus()).startedAt(source.getStartedAt()).deadlineAt(source.getDeadlineAt())
                .endedAt(source.getEndedAt()).claimExpiresAt(source.getClaimExpiresAt())
                .correctCount(source.getCorrectCount()).placementLevel(source.getPlacementLevel())
                .revision(source.getRevision())
                .answers(new ArrayList<>(source.getAnswers().stream()
                        .map(a -> new PlacementAnswerVo(a.getQuestionId(), a.getSelectedOptionId(), a.getAnsweredAt()))
                        .toList()))
                .build();
    }

    static PlacementTestSet testSet(PlacementTestSet source) {
        return fit.iuh.engolearn.placement.mapper.PlacementSnapshotMapper.copy(source, audio -> audio);
    }
}
