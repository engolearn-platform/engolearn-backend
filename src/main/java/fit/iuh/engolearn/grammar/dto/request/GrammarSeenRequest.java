package fit.iuh.engolearn.grammar.dto.request;

import jakarta.validation.constraints.Positive;

public record GrammarSeenRequest(@Positive int contentVersion) {
}
