package fit.iuh.engolearn.companion.service;

import java.util.List;
import java.util.Map;

public interface CompanionToolHandler {
    List<CompanionToolDefinition> definitions();
    String execute(String name, Map<String, Object> arguments, CompanionToolExecutionContext context);
}
