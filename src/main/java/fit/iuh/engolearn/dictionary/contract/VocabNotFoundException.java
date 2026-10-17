package fit.iuh.engolearn.dictionary.contract;

/**
 * Contract-level failure for dictionary lookups. Thrown instead of leaking
 * persistence exceptions across the feature boundary.
 */
public class VocabNotFoundException extends RuntimeException {

    public VocabNotFoundException(String message) {
        super(message);
    }
}
