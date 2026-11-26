package fit.iuh.engolearn.topic.service.impl;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.topic.contract.dto.TopicSpeakingProfileDto;
import fit.iuh.engolearn.topic.contract.dto.TopicSpeakingResourceDto;
import fit.iuh.engolearn.topic.service.TopicSpeakingOpenability.ReleasedScope;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TopicSpeakingContextBudgetTest {
    @Test
    void countsSerializedBytesAndKeepsTheEntireContextAtTheLimit() {
        var mapper = new ObjectMapper();
        var scope = new ReleasedScope("Cafe", "Cafe", null, "v1", List.of());
        var profile = new TopicSpeakingProfileDto(true, CefrLevel.A1, "Order a drink", "Barista",
                "Learner", List.of(), List.of("resource"), "Good morning!", null);
        var resources = List.of(new TopicSpeakingResourceDto("resource", TopicSpeakingResourceDto.Type.EXPRESSION,
                "Can I have tea?", "item", "Gọi trà"));
        int budget = TopicSpeakingContextBudget.DYNAMIC_RESERVE_BYTES + 1;
        while (!new TopicSpeakingContextBudget(mapper, budget).fits(scope, profile, resources)) budget++;

        assertThat(new TopicSpeakingContextBudget(mapper, budget).fits(scope, profile, resources)).isTrue();
        assertThat(new TopicSpeakingContextBudget(mapper, budget - 1).fits(scope, profile, resources)).isFalse();
        assertThat(new TopicSpeakingContextBudget(mapper, budget).fits(scope, profile, List.of(
                resources.getFirst(), new TopicSpeakingResourceDto("second", TopicSpeakingResourceDto.Type.EXPRESSION,
                        "Do you have coffee?", "item", "Hỏi về cà phê")))).isFalse();
    }
}
