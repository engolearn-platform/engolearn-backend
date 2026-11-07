package fit.iuh.engolearn.placement.service;

import fit.iuh.engolearn.placement.dto.response.PlacementClaimGateResponse;

public interface PlacementClaimService {
    PlacementClaimGateResponse getClaimGate(String attemptId, PlacementAccessContext access);
}
