package fit.iuh.engolearn.placement.service.impl;

import fit.iuh.engolearn.placement.contract.dto.PlacementManagementSummaryDto;
import fit.iuh.engolearn.placement.contract.PlacementManagementSummaryProvider;
import fit.iuh.engolearn.placement.repository.PlacementAdminTestSetRepository;
import fit.iuh.engolearn.placement.repository.PlacementQuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PlacementManagementSummaryProviderImpl implements PlacementManagementSummaryProvider {
    private final PlacementAdminTestSetRepository testSets;
    private final PlacementQuestionRepository questions;

    @Override
    public PlacementManagementSummaryDto managementSummary() {
        return new PlacementManagementSummaryDto(testSets.countAll(), questions.countAll());
    }
}
