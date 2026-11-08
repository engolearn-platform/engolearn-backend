package fit.iuh.engolearn.user.service.impl;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.user.contract.UserLevelPromotion;
import fit.iuh.engolearn.user.contract.exception.UserLevelPromotionException;
import fit.iuh.engolearn.user.repository.UserLevelPromotionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserLevelPromotionImpl implements UserLevelPromotion {
    private static final List<CefrLevel> RANKS = List.of(CefrLevel.A1, CefrLevel.A2, CefrLevel.B1,
            CefrLevel.B2, CefrLevel.C1, CefrLevel.C2);
    private final UserLevelPromotionRepository levels;

    @Override
    @Transactional(transactionManager = "mongoTransactionManager", propagation = Propagation.MANDATORY)
    public CefrLevel promote(String userId, CefrLevel requested) {
        if (userId == null || userId.isBlank() || requested == null) throw new IllegalArgumentException("User and level are required");
        try {
            int rank = RANKS.indexOf(requested);
            CefrLevel persisted = levels.promoteIfLower(userId, RANKS.subList(0, rank), requested)
                    .orElseThrow(UserLevelPromotionException::new);
            if (RANKS.indexOf(persisted) < rank) throw new UserLevelPromotionException();
            return persisted;
        } catch (UserLevelPromotionException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new UserLevelPromotionException(failure);
        }
    }
}
