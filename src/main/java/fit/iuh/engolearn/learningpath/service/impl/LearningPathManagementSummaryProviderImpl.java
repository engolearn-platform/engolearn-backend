package fit.iuh.engolearn.learningpath.service.impl;

import fit.iuh.engolearn.learningpath.contract.dto.LearningPathManagementSummaryDto;
import fit.iuh.engolearn.learningpath.contract.LearningPathManagementSummaryProvider;
import fit.iuh.engolearn.learningpath.repository.QuestionPoolPassageRepository;
import fit.iuh.engolearn.learningpath.repository.QuestionPoolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('CONTENT_MANAGER')")
public class LearningPathManagementSummaryProviderImpl implements LearningPathManagementSummaryProvider {
    private final QuestionPoolPassageRepository passages;
    private final QuestionPoolRepository questions;

    @Override
    public LearningPathManagementSummaryDto managementSummary() {
        var counts = passages.countByStatus();
        return new LearningPathManagementSummaryDto(counts.total(), counts.active(), counts.draft(),
                counts.retired(), questions.countAll());
    }
}
