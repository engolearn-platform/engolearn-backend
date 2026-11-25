package fit.iuh.engolearn.companion.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CompanionTurnRequestTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void requiresTurnIdAndNonBlankContent() {
        var invalid = new CompanionTurnRequest(null, "  ", false);
        assertThat(validator.validate(invalid)).hasSize(2);
    }

    @Test
    void acceptsTurnRequestWithContent() {
        var request = new CompanionTurnRequest(UUID.randomUUID(), "Hello", false);
        assertThat(validator.validate(request)).isEmpty();
    }
}
