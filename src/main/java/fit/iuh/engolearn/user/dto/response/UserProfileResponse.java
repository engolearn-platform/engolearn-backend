package fit.iuh.engolearn.user.dto.response;

import fit.iuh.engolearn.shared.enums.CefrLevel;

public record UserProfileResponse(String displayName, CefrLevel cefrLevel) {}
