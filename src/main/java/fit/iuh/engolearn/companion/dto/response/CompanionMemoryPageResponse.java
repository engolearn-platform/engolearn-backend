package fit.iuh.engolearn.companion.dto.response;

import java.util.List;

public record CompanionMemoryPageResponse(List<CompanionMemoryResponse> items, String nextCursor) { }
