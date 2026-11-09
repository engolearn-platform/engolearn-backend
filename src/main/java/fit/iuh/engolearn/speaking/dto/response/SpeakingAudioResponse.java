package fit.iuh.engolearn.speaking.dto.response;

import java.net.URI;

public record SpeakingAudioResponse(URI url, long expiresInSeconds) {}
