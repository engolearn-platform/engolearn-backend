package fit.iuh.engolearn.speaking.utils.port;

import fit.iuh.engolearn.speaking.model.GatewayOutcome;
import fit.iuh.engolearn.speaking.model.SpeakingAiRequestV1;

public interface SpeakingAiGateway {
    GatewayOutcome assess(SpeakingAiRequestV1 request);
}
