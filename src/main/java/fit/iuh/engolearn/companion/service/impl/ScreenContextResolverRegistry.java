package fit.iuh.engolearn.companion.service.impl;

import fit.iuh.engolearn.companion.dto.request.ScreenContextReferenceRequest;
import fit.iuh.engolearn.companion.exception.CompanionException;
import fit.iuh.engolearn.companion.model.enums.ScreenContextType;
import fit.iuh.engolearn.companion.service.ScreenContextResolver;
import fit.iuh.engolearn.config.CompanionProperties;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ScreenContextResolverRegistry {
    private final Map<ScreenContextType, ScreenContextResolver<?>> resolvers;
    private final ObjectMapper mapper;
    private final CompanionProperties properties;

    public ScreenContextResolverRegistry(List<ScreenContextResolver<?>> registered, ObjectMapper mapper, CompanionProperties properties) {
        var index = new EnumMap<ScreenContextType, ScreenContextResolver<?>>(ScreenContextType.class);
        for (var resolver : registered) {
            if (index.putIfAbsent(resolver.supportedType(), resolver) != null)
                throw new IllegalStateException("Duplicate screen context resolver: " + resolver.supportedType());
        }
        this.resolvers = Map.copyOf(index);
        this.mapper = mapper;
        this.properties = properties;
    }

    public void validate(ScreenContextReferenceRequest ref) {
        if (ref == null) return;
        var resolver = ref.type() == null ? null : resolvers.get(ref.type());
        if (resolver == null || !resolver.accepts(ref))
            throw new CompanionException(400, "INVALID_SCREEN_CONTEXT", "The selected screen context is invalid.", false);
    }

    public String resolve(String learnerId, ScreenContextReferenceRequest ref) {
        validate(ref);
        var payload = resolvers.get(ref.type()).resolve(learnerId, ref)
                .orElseThrow(() -> new CompanionException(409, "SCREEN_CONTEXT_UNAVAILABLE",
                        "The selected learning content is no longer available. Send a new message with an available context.", false));
        String json = mapper.writeValueAsString(payload);
        if (json.getBytes(StandardCharsets.UTF_8).length > properties.getScreenContextMaxBytes())
            throw new CompanionException(422, "SCREEN_CONTEXT_TOO_LARGE",
                    "The selected learning content is too large to include. Ask about a smaller learning context.", false);
        return json;
    }
}
