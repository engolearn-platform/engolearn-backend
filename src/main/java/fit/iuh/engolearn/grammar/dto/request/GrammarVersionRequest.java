package fit.iuh.engolearn.grammar.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record GrammarVersionRequest(@NotNull @PositiveOrZero Long version, Boolean confirmPublish) {
}
