package fit.iuh.engolearn.speaking.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;
import fit.iuh.engolearn.topic.contract.dto.TopicSpeakingResourceDto;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SpeakingResourceVo implements EmbeddedDocument {
    @Field("id") private String id;
    @Field("type") private TopicSpeakingResourceDto.Type type;
    @Field("text_en") private String textEn;
    @Field("context") private String context;

    public SpeakingResourceVo(String id, TopicSpeakingResourceDto.Type type, String textEn) {
        this(id, type, textEn, null);
    }
}
