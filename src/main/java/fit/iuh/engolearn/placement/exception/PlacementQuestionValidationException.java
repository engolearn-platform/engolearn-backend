package fit.iuh.engolearn.placement.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class PlacementQuestionValidationException extends PlacementRequestException {
    private final Map<String, String> fields;

    public PlacementQuestionValidationException(Map<String, String> fields) {
        super(HttpStatus.BAD_REQUEST, "PLACEMENT_QUESTION_INVALID", "One or more question fields are invalid.");
        this.fields = Map.copyOf(fields);
    }

    public Map<String, String> fields() {
        return fields;
    }
}
