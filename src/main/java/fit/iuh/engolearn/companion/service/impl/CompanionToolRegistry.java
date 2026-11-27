package fit.iuh.engolearn.companion.service.impl;

import fit.iuh.engolearn.companion.service.CompanionMemoryToolContext;
import fit.iuh.engolearn.companion.service.CompanionBusinessTurnContext;
import fit.iuh.engolearn.companion.service.CompanionToolDefinition;
import fit.iuh.engolearn.companion.service.CompanionToolExecutionContext;
import fit.iuh.engolearn.companion.service.CompanionToolHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CompanionToolRegistry {
    private final List<CompanionToolDefinition> definitions;
    private final Map<String, CompanionToolHandler> handlers;

    public CompanionToolRegistry(List<CompanionToolHandler> registered) {
        var declarations = new ArrayList<CompanionToolDefinition>();
        var routes = new HashMap<String, CompanionToolHandler>();
        for (CompanionToolHandler handler : registered) {
            for (CompanionToolDefinition definition : handler.definitions()) {
                if (routes.putIfAbsent(definition.name(), handler) != null)
                    throw new IllegalStateException("Duplicate companion tool: " + definition.name());
                declarations.add(definition);
            }
        }
        definitions = List.copyOf(declarations);
        handlers = Map.copyOf(routes);
    }

    public String safeName(String name) {
        return name != null && handlers.containsKey(name) ? name : "UNKNOWN_TOOL";
    }

    public List<CompanionToolDefinition> definitions(boolean enabled) {
        return enabled ? definitions : List.of();
    }

    public String execute(String name, Map<String, Object> arguments, String learnerId) {
        return execute(name, arguments, learnerId, () -> {});
    }

    public String execute(String name, Map<String, Object> arguments, String learnerId, Runnable onAssessmentLookup) {
        return execute(name, arguments, learnerId, onAssessmentLookup, null);
    }

    public String execute(String name, Map<String, Object> arguments, String learnerId,
            Runnable onAssessmentLookup, CompanionMemoryToolContext memoryContext) {
        return execute(name, arguments, learnerId, onAssessmentLookup, memoryContext, null);
    }

    public String execute(String name, Map<String, Object> arguments, String learnerId,
            Runnable onAssessmentLookup, CompanionMemoryToolContext memoryContext,
            CompanionBusinessTurnContext businessContext) {
        CompanionToolHandler handler = name == null ? null : handlers.get(name);
        if (handler == null) return "{\"status\":\"ERROR\",\"message\":\"The requested tool is unavailable.\"}";
        return handler.execute(name, arguments, new CompanionToolExecutionContext(learnerId, onAssessmentLookup, memoryContext, businessContext));
    }
}
