package fit.iuh.engolearn.dictionary.contract;

import fit.iuh.engolearn.dictionary.contract.dto.VocabSenseDto;

import java.util.Optional;

/**
 * Cross-feature capability of the dictionary: resolve a vocabulary sense by id.
 * Designed as if the provider were remote — {@link Optional} for "not found",
 * {@link VocabNotFoundException} for business failures, no persistence types.
 */
public interface VocabularyLookup {

    Optional<VocabSenseDto> findSense(String vocabId, String senseId);
}
