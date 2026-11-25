package fit.iuh.engolearn.speaking.utils;

import fit.iuh.engolearn.speaking.model.enums.SpeakingTurnStatus;
import fit.iuh.engolearn.speaking.model.vo.SpeakingSnapshotVo;
import fit.iuh.engolearn.speaking.model.vo.SpeakingTurnVo;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

class SpeakingConversationTest {
    private final SpeakingConversation conversation = new SpeakingConversation(JsonMapper.builder().build());

    @Test
    void invalidOptionalFieldsCannotRejectReplyOrValidOperations() {
        var result = conversation.parse("""
                {"reply":"Orange juice it is.",
                 "hint":{"exampleEn":"Thanks","explanationVi":42},
                 "correction":{"type":"GRAMMAR","original":"fabricated","suggested":"I want some","explanationVi":"Sửa"},
                 "memoryDelta":[{"operation":"UPSERT","target":"FACT","key":"drink","value":"Orange juice"},
                                {"operation":"REMOVE","target":"FACT","key":"other","value":"invalid"}]}
                """, "I want juice");
        assertThat(result.reply()).isEqualTo("Orange juice it is.");
        assertThat(result.hint()).isNull();
        assertThat(result.correction()).isNull();
        assertThat(result.delta()).hasSize(1);
        assertThat(conversation.parse("{\"reply\":\"   \"}", "juice")).isNull();
        assertThat(conversation.parse("{", "juice")).isNull();
    }

    @Test
    void contextUsesFiveCompletedExchangesAndReplaysMemoryOutsideTheWindow() {
        var first = conversation.parse("""
                {"reply":"Welcome","memoryDelta":[{"operation":"UPSERT","target":"FACT","key":"drink","value":"Tea"}]}
                """, "Tea");
        var history = IntStream.rangeClosed(1, 9).mapToObj(i -> SpeakingTurnVo.builder().sequence(i)
                .status(i == 8 ? SpeakingTurnStatus.FAILED : SpeakingTurnStatus.COMMITTED)
                .transcript("Turn " + i).reply("Reply " + i)
                .memoryDelta(i == 1 ? first.delta() : List.of()).build()).toList();
        var memory = SpeakingConversation.replay(null, history);
        assertThat(memory.getFacts()).containsEntry("drink", "Tea");
        assertThat(memory.getProcessedThroughTurn()).isEqualTo(9);
        assertThat(SpeakingConversation.replay(memory, history)).isSameAs(memory);
        var context = conversation.context(SpeakingSnapshotVo.builder().title("Cafe").build(), "Welcome",
                memory, history, "Yes, please!", 720);
        assertThat(context.messages()).extracting(SpeakingConversation.Message::text).containsExactly(
                "Turn 4", "Reply 4", "Turn 5", "Reply 5", "Turn 6", "Reply 6", "Turn 7", "Reply 7",
                "Turn 9", "Reply 9", "Yes, please!");
        assertThat(context.messages()).extracting(SpeakingConversation.Message::role)
                .containsExactly("user", "assistant", "user", "assistant", "user", "assistant", "user",
                        "assistant", "user", "assistant", "user");
        assertThat(context.data()).contains("Cafe", "Tea", "720");
    }

    @Test
    void replayBoundsFactCountUpdatesKnownFactAndAdvancesOnNoOp() {
        var rows = new java.util.ArrayList<SpeakingTurnVo>();
        for (int sequence = 1; sequence <= 31; sequence++) {
            var delta = new fit.iuh.engolearn.speaking.model.vo.SpeakingMemoryVo.Delta(
                    fit.iuh.engolearn.speaking.model.vo.SpeakingMemoryVo.Operation.UPSERT,
                    fit.iuh.engolearn.speaking.model.vo.SpeakingMemoryVo.Target.FACT, "fact-" + sequence, "tea");
            rows.add(SpeakingTurnVo.builder().sequence(sequence).status(SpeakingTurnStatus.COMMITTED)
                    .memoryDelta(List.of(delta)).build());
        }
        var full = SpeakingConversation.replay(null, rows);
        assertThat(full.getFacts()).hasSize(30).doesNotContainKey("fact-31");
        assertThat(full.getVersion()).isEqualTo(30);
        assertThat(full.getProcessedThroughTurn()).isEqualTo(31);

        var response = conversation.parse("""
                {"reply":"Sure","memoryDelta":[
                  {"operation":"UPSERT","target":"FACT","key":"fact-1","value":"juice"},
                  {"operation":"REMOVE","target":"FACT","key":"fact-2","value":null},
                  {"operation":"UPSERT","target":"OPEN_THREAD","key":"followup"},
                  {"operation":"UPSERT","target":"COVERED_TOPIC","key":"drinks","value":null}]}
                """, "Juice, please");
        rows.add(SpeakingTurnVo.builder().sequence(32).status(SpeakingTurnStatus.COMMITTED)
                .memoryDelta(response.delta()).build());
        rows.add(SpeakingTurnVo.builder().sequence(33).status(SpeakingTurnStatus.COMMITTED)
                .memoryDelta(List.of()).build());
        var replayed = SpeakingConversation.replay(full, rows);
        assertThat(replayed.getFacts()).containsEntry("fact-1", "juice").doesNotContainKey("fact-2");
        assertThat(replayed.getOpenThreads()).containsExactly("followup");
        assertThat(replayed.getCoveredTopics()).containsExactly("drinks");
        assertThat(replayed.getVersion()).isEqualTo(31);
        assertThat(replayed.getProcessedThroughTurn()).isEqualTo(33);
    }
}
