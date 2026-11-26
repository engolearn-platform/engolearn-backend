package fit.iuh.engolearn.user.service.impl;

import fit.iuh.engolearn.user.dto.response.UserProfileResponse;
import fit.iuh.engolearn.user.exception.ProfileNotFoundException;
import fit.iuh.engolearn.user.exception.InvalidDisplayNameException;
import fit.iuh.engolearn.user.repository.UserProfileNameRepository;
import fit.iuh.engolearn.user.repository.UserProfileRepository;
import fit.iuh.engolearn.user.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {
    private final UserProfileRepository profiles;
    private final UserProfileNameRepository names;

    @Override
    public UserProfileResponse getCurrentProfile(String userId) {
        var profile = profiles.findById(userId)
                .orElseThrow(() -> new ProfileNotFoundException());
        return new UserProfileResponse(profile.getDisplayName(), profile.getCefrLevel());
    }

    @Override
    public UserProfileResponse updateDisplayName(String userId, String displayName) {
        String normalized = displayName == null ? null : displayName.strip();
        if (normalized != null && (normalized.isEmpty() || normalized.length() > 80)) {
            throw new InvalidDisplayNameException();
        }
        var profile = names.updateDisplayName(userId, normalized)
                .orElseThrow(ProfileNotFoundException::new);
        return new UserProfileResponse(profile.getDisplayName(), profile.getCefrLevel());
    }
}
