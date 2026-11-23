package fit.iuh.engolearn.grammar.exception;

import fit.iuh.engolearn.grammar.dto.response.GrammarChecklistResponse;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class GrammarRequestException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final GrammarChecklistResponse checklist;

    public GrammarRequestException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public GrammarRequestException(HttpStatus status, String code, String message, GrammarChecklistResponse checklist) {
        super(message);
        this.status = status;
        this.code = code;
        this.checklist = checklist;
    }
}
