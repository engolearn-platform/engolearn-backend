package fit.iuh.engolearn.companion.model;

import fit.iuh.engolearn.shared.BaseDocument;
import java.time.Instant;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("companion_memory_operation_receipts")
@CompoundIndex(name = "memory_operation_ordinal_unique", def = "{'conversation_id':1,'turn_id':1,'ordinal':1}", unique = true)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class MemoryOperationReceipt extends BaseDocument {
    public static String idFor(String conversationId, String turnId, int ordinal) {
        return java.util.UUID.nameUUIDFromBytes((conversationId + ":" + turnId + ":" + ordinal)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }
    @Field("conversation_id") private String conversationId;
    @Field("turn_id") private String turnId;
    @Field("ordinal") private int ordinal;
    @Field("fingerprint") private String fingerprint;
    @Field("action") private String action;
    @Field("memory_id") private String memoryId;
    @Field("memory_version") private long memoryVersion;
    @Field("forget_revision") private long forgetRevision;
    @Field("result_code") private String resultCode;
    @Field("committed_at") private Instant committedAt;
}
