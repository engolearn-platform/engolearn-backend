package fit.iuh.engolearn.user.service.impl;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.user.contract.UserLevelLookup;
import fit.iuh.engolearn.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserLevelLookupImpl implements UserLevelLookup {
    private final UserProfileRepository profiles;

    @Override
    public Optional<CefrLevel> findLevel(String userId) {
        return profiles.findById(userId).map(profile -> profile.getCefrLevel());
    }
}
