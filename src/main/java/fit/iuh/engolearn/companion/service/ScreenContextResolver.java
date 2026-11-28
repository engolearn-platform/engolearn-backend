package fit.iuh.engolearn.companion.service;

import fit.iuh.engolearn.companion.dto.request.ScreenContextReferenceRequest;
import fit.iuh.engolearn.companion.model.enums.ScreenContextType;
import java.util.Optional;

public interface ScreenContextResolver<T> {
    ScreenContextType supportedType();
    boolean accepts(ScreenContextReferenceRequest reference);
    Optional<T> resolve(String learnerId, ScreenContextReferenceRequest reference);
}
