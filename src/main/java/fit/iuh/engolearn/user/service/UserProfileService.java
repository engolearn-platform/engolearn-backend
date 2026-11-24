package fit.iuh.engolearn.user.service;

import fit.iuh.engolearn.user.dto.response.UserProfileResponse;

public interface UserProfileService {
    UserProfileResponse getCurrentProfile(String userId);
    UserProfileResponse updateDisplayName(String userId, String displayName);
}
