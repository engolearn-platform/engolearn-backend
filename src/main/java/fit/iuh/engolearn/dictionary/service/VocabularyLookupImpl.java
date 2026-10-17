package fit.iuh.engolearn.dictionary.service;

import fit.iuh.engolearn.dictionary.contract.VocabularyLookup;
import fit.iuh.engolearn.dictionary.contract.dto.VocabSenseDto;
import fit.iuh.engolearn.dictionary.repository.VocabularyRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Implementation of {@link VocabularyLookup}. Contract shell: no caller consumes it yet,
 * so the mapping stays deliberately minimal (vocabulary id + word + requested sense id).
 */
@Service
public class VocabularyLookupImpl implements VocabularyLookup {

    private final VocabularyRepository vocabularyRepository;

    public VocabularyLookupImpl(VocabularyRepository vocabularyRepository) {
        this.vocabularyRepository = vocabularyRepository;
    }

    @Override
    public Optional<VocabSenseDto> findSense(String vocabId, String senseId) {
        return vocabularyRepository.findById(vocabId)
                .map(vocabulary -> new VocabSenseDto(vocabulary.getId(), vocabulary.getWord(), senseId));
    }
}
