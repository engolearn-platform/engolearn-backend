package fit.iuh.engolearn.dictionary.contract.dto;

/**
 * Stable contract view of a vocabulary sense. Part of the dictionary contract:
 * meant for other features, never shaped for the frontend.
 */
public record VocabSenseDto(String vocabId, String word, String sense) {
}
