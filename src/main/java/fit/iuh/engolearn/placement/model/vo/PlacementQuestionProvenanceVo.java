package fit.iuh.engolearn.placement.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;
import java.util.List;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PlacementQuestionProvenanceVo implements EmbeddedDocument {
    @Field("standalone") private boolean standalone;
    @Field("adaptation_type") private String adaptationType;
    @Field("author") private String author;
    @Field("authored_on") private String authoredOn;
    @Field("revision") private int revision;
    @Field("previous_draft_question_id") private String previousDraftQuestionId;
    @Field("source_url") private String sourceUrl;
    @Field("source_level") private String sourceLevel;
    @Field("level_status") private String levelStatus;
    @Field("human_review_status") private String humanReviewStatus;
    @Field("verified_by") private String verifiedBy;
    @Field("verified_at") private String verifiedAt;
    @Field("content_group_id") private String contentGroupId;
    @Field("title") private String title;
    @Field("answer_evidence") private String answerEvidence;
    @Field("answer_verification") private String answerVerification;
    @Field("level_rationale") private String levelRationale;
    @Field("distractor_rationales") private List<PlacementDistractorRationaleVo> distractorRationales;
    @Field("timing_policy") private PlacementTimingPolicyVo timingPolicy;
    @Field("audio_group_id") private String audioGroupId;
    @Field("audio_type") private String audioType;
    @Field("audio_file") private String audioFile;
    @Field("audio_sha256") private String audioSha256;
    @Field("audio_duration_seconds") private double audioDurationSeconds;
    @Field("audio_clip_start_seconds") private double audioClipStartSeconds;
    @Field("audio_clip_end_seconds") private double audioClipEndSeconds;
    @Field("answer_evidence_start_seconds") private Double answerEvidenceStartSeconds;
    @Field("answer_evidence_end_seconds") private Double answerEvidenceEndSeconds;
    @Field("transcript_scope") private String transcriptScope;
    @Field("audio_review_status") private String audioReviewStatus;
    @Field("semantic_family_id") private String semanticFamilyId;
    @Field("assembly_note") private String assemblyNote;
}
