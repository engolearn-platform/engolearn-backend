package fit.iuh.engolearn.user.service.impl;

import fit.iuh.engolearn.user.contract.UserAssessmentProgress;
import fit.iuh.engolearn.user.contract.dto.UserAssessmentProgressDto;
import fit.iuh.engolearn.user.contract.exception.UserLevelPromotionException;
import fit.iuh.engolearn.user.model.vo.UserAssessmentProgressVo;
import fit.iuh.engolearn.user.repository.UserAssessmentProgressRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserAssessmentProgressImpl implements UserAssessmentProgress {
    private final UserAssessmentProgressRepository progress;

    @Override
    public Optional<UserAssessmentProgressDto> find(String userId, boolean includeDevelopment) {
        return progress.find(userId).or(() -> progress.findLegacy(userId))
                .filter(levels -> includeDevelopment || !levels.developmentData())
                .map(levels -> new UserAssessmentProgressDto(levels.initialLevel(), levels.currentLevel(),
                        levels.highestLevel(), levels.latestValidAt(), levels.latestValidAttemptId(),
                        levels.developmentData()));
    }

    @Override
    @Transactional(transactionManager = "mongoTransactionManager", propagation = Propagation.MANDATORY)
    public void save(String userId, UserAssessmentProgressDto levels) {
        var state = new UserAssessmentProgressVo(levels.initialLevel(), levels.currentLevel(),
                levels.highestLevel(), levels.latestValidAt(), levels.latestValidAttemptId(),
                levels.developmentData());
        if (!progress.update(userId, state)) throw new UserLevelPromotionException();
    }
}
